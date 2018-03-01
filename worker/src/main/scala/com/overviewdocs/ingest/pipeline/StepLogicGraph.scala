package com.overviewdocs.ingest.pipeline

import akka.stream.{FanOutShape2,FlowShape,Graph,Materializer,SourceShape}
import akka.stream.scaladsl.{Flow,GraphDSL,Partition,Source}
import akka.util.ByteString
import scala.concurrent.{ExecutionContext,Future,Promise}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.models.{CreatedFile2,WrittenFile2,ProcessedFile2}

/** Runs StepLogic on a stream of WrittenFile2s.
  *
  * This is a fan-out Graph: it produces WrittenFile2s to pass to the next
  * pipeline step and ProcessedFile2s to pass to the ingester.
  *
  * `writtenFile2.onProgress()` will be called every time the StepLogic outputs
  * Progress fragments. `writtenFile2.canceled` should be handled by the
  * StepLogic.
  *
  * This Graph converts and outputs its _inputs_, too. Each input element will
  * transition from WRITTEN to PROCESSED (while being written to the database),
  * and it will be emitted. It will have `nChildren` and may have a
  * `processingError`. Even after cancellation, the StepLogic will complete; in
  * that case, `processingError` will become `"canceled"`.
  *
  *               +---------------------------------------+
  *               |         StepLogic substreams          |
  *               |              +-------+                |
  *               |              | logic |                |
  *               |              +-------+                |
  *               |             /         \               |
  *               |  +---------o           o-----------+  | WrittenFile2
  *               |  | process | +-------+ | partition o~~O    ~> out0
  *     in  ~>    O~~o         o~| logic |~o           |  |
  * WrittenFile2  |  |         | +-------+ |           o~~O    ~> out1
  *               |  +---------o           o-----------+  | ProcessedFile2
  *               |             \         /               |
  *               |              +-------+                |
  *               |              | logic |                |
  *               |              +-------+                |
  *               +---------------------------------------+
  *
  * About `parallelism`: the parameter is the _maximum_ number of simultaneous
  * conversions -- even if there are more workers than that.
  *
  * There is a potential deadlock in buffering: a StepLogic that outputs lots of
  * WrittenFile2s meant for recursion (e.g., zipfiles full of zipfiles) will
  * eventually receive them again as input: a feedback loop. The solution: the
  * caller should buffer the output. Assume a WrittenFile2 consumes ~200 bytes
  * plus 5kb of metadata. A 10K-element output buffer could consume 50MB and
  * would allow extracting a zipfile full of 10k zipfiles. (Future idea: it
  * would be more scalable to just store WrittenFile2 IDs and read them from the
  * database if there are too many.)
  */
class StepLogicGraph(logic: StepLogic, file2Writer: File2Writer, parallelism: Int) {
  /** Converts a File2 from WRITTEN to PROCESSED and outputs its WRITTEN
    * children as they come. (Just the children! Not itself.)
    */
  def graph(implicit ec: ExecutionContext, mat: Materializer): Graph[FanOutShape2[WrittenFile2, WrittenFile2, ProcessedFile2], akka.NotUsed] = {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      type ToFanOut = Either[WrittenFile2, ProcessedFile2]

      val process: FlowShape[WrittenFile2, ToFanOut] = builder.add(
        Flow.apply[WrittenFile2].flatMapMerge(parallelism, singleFileSource _)
      )

      val partition = builder.add(
        Partition[ToFanOut](2, x => if (x.isLeft) { 0 } else { 1 })
      )

      val written: FlowShape[ToFanOut, WrittenFile2] = builder.add(
        Flow.apply[ToFanOut].collect { case Left(w) => w }
      )

      val processed: FlowShape[ToFanOut, ProcessedFile2] = builder.add(
        Flow.apply[ToFanOut].collect { case Right(p) => p }
      )

      process.out ~> partition ~> written
                     partition ~> processed

      new FanOutShape2(process.in, written.out, processed.out)
    }
  }

  private def singleFileSource(
    parentFile2: WrittenFile2
  )(implicit ec: ExecutionContext, mat: Materializer): Graph[SourceShape[Either[WrittenFile2, ProcessedFile2]], akka.NotUsed] = {
    /*
     * We process the incoming stream of StepOutputFragments, passing each to
     * a (side-effect producing) File2Writer method and emitting each child
     * File2 when it's ready -- including one final emit after the input stream
     * completes.
     *
     * akka-stream doesn't have exactly what we want. Its foldAsync() is great
     * for generating side-effects and maintaining state, but it doesn't emit
     * anything. scanAsync() is great for emitting, but it _always_ emits.
     *
     * So we compose: onFragment emits an Option[element] _and_ the State.
     */

    sealed trait State
    case object Start extends State
    case class AtChild(child: CreatedFile2) extends State
    case object End extends State

    type ToFanOut = Either[WrittenFile2, ProcessedFile2]

    case class ScanResult(
      toEmit: Vector[ToFanOut],
      state: State
    )

    def onFragment(lastEmitAndState: ScanResult, fragment: StepOutputFragment): Future[ScanResult] = {
      val state = lastEmitAndState.state
      (state, fragment) match {
        case (_, p: StepOutputFragment.Progress) => { parentFile2.onProgress(p.fraction); ignore(state) }
        case (End, _) => ignore(state) // we've marked the parent as processed: garbage can't change our minds
        case (Start, h: StepOutputFragment.File2Header) => createChild(None, h)
        case (Start, e: StepOutputFragment.EndFragment) => end(None, e)
        case (Start, f) => unexpectedFragment(f, None)
        case (AtChild(child), StepOutputFragment.Blob(stream)) => addBlob(child, stream)
        case (AtChild(child), StepOutputFragment.InheritBlob) => inheritBlob(child)
        case (AtChild(child), StepOutputFragment.Text(stream)) => addText(child, stream)
        case (AtChild(child), StepOutputFragment.Thumbnail(ct, stream)) => addThumbnail(child, ct, stream)
        case (AtChild(child), h: StepOutputFragment.File2Header) => createChild(Some(child), h)
        case (AtChild(child), e: StepOutputFragment.EndFragment) => end(Some(child), e)
      }
    }

    def ignore(lastState: State): Future[ScanResult] = {
      Future.successful(ScanResult(Vector(), lastState))
    }

    def error(message: String, currentChild: Option[CreatedFile2]): Future[ScanResult] = {
      currentChild match {
        case Some(child) => {
          // We'll delete the current child File2: it isn't WRITTEN
          val nPreviousChildren = child.indexInParent
          for {
            _ <- file2Writer.delete(child)
            parentProcessed <- writeParent(nPreviousChildren, Some(message))
          } yield ScanResult(Vector(parentProcessed), End)
        }
        case None => {
          for {
            parentProcessed <- writeParent(0, Some(message))
          } yield ScanResult(Vector(parentProcessed), End)
        }
      }
    }

    def logicError(message: String, currentChild: Option[CreatedFile2]): Future[ScanResult] = {
      error("logic error: " + message, currentChild)
    }

    def unexpectedFragment(f: StepOutputFragment, currentChild: Option[CreatedFile2]): Future[ScanResult] = {
      logicError("unexpected fragment " + f.getClass.toString, currentChild)
    }

    def writeLastChildOpt(lastChildOpt: Option[CreatedFile2]): Future[Vector[ToFanOut]] = {
      lastChildOpt match {
        case Some(child) if child.pipelineOptions.stepsRemaining.isEmpty => {
          file2Writer.setWrittenAndProcessed(child).map(p => Vector(Right(p)))
        }
        case Some(child) => {
          file2Writer.setWritten(child).map(w => Vector(Left(w)))
        }
        case None => Future.successful(Vector.empty)
      }
    }

    def createChild(lastChild: Option[CreatedFile2], header: StepOutputFragment.File2Header): Future[ScanResult] = {
      // If header is invalid (has wrong indexInParent), move to error state
      // Otherwise, emit lastChild (which may be None) and move to AtChild
      if (lastChild.map(_.blobOpt) == Some(None)) {
        unexpectedFragment(header, lastChild)
      } else {
        for {
          lastChildWritten <- writeLastChildOpt(lastChild)
          nextChild <- file2Writer.createChild(
            parentFile2,
            lastChild.map(_.indexInParent + 1).getOrElse(0),
            header.filename,
            header.contentType,
            header.languageCode,
            header.metadata,
            header.pipelineOptions
          )
        } yield ScanResult(lastChildWritten, AtChild(nextChild))
      }
    }

    def addBlob(child: CreatedFile2, data: Source[ByteString, _]): Future[ScanResult] = {
      for {
        writtenChild <- file2Writer.writeBlob(child, data)
      } yield ScanResult(Vector(), AtChild(writtenChild))
    }

    def addThumbnail(child: CreatedFile2, contentType: String, data: Source[ByteString, _]): Future[ScanResult] = {
      for {
        writtenChild <- file2Writer.writeThumbnail(child, contentType, data)
      } yield ScanResult(Vector(), AtChild(writtenChild))
    }

    def addText(child: CreatedFile2, data: Source[ByteString, _]): Future[ScanResult] = {
      for {
        writtenChild <- file2Writer.writeText(child, data)
      } yield ScanResult(Vector(), AtChild(writtenChild))
    }

    def inheritBlob(child: CreatedFile2): Future[ScanResult] = {
      if (child.indexInParent != 0) {
        logicError("tried to inherit blob when indexInParent!=0", Some(child))
      } else {
        for {
          writtenChild <- file2Writer.writeBlobStorageRef(child, parentFile2.blob)
        } yield ScanResult(Vector(), AtChild(writtenChild))
      }
    }

    def end(lastChildOpt: Option[CreatedFile2], fragment: StepOutputFragment.EndFragment): Future[ScanResult] = {
      (lastChildOpt, fragment) match {
        case (_, StepOutputFragment.FileError(message)) => error(message, lastChildOpt)
        case (_, StepOutputFragment.StepError(ex)) => error("step error: " + ex.getMessage, lastChildOpt)
        case (_, StepOutputFragment.Canceled) => error("canceled", lastChildOpt)
        case (Some(child), _) if (child.blobOpt.isEmpty) => logicError("tried to write child without blob data", lastChildOpt)
        case (_, StepOutputFragment.Done) => for {
          lastChildWritten: Vector[ToFanOut] <- writeLastChildOpt(lastChildOpt)
          parentProcessed <- writeParent(lastChildOpt.map(_.indexInParent + 1).getOrElse(0), None)
        } yield ScanResult(lastChildWritten :+ parentProcessed, End)
      }
    }

    def writeParent(nChildren: Int, error: Option[String]): Future[ToFanOut] = {
      file2Writer.setProcessed(parentFile2, nChildren, error).map(Right.apply _)
    }

    logic.toChildFragments(parentFile2)                   // StepOutputFragment
      .scanAsync(ScanResult(Vector(), Start))(onFragment) // ScanResult
      .mapConcat(_.toEmit)                                // Either[WrittenFile2,ProcessedFile2]
  }
}
