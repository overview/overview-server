package com.overviewdocs.ingest.pipeline

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Source}
import akka.util.ByteString
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.models.{ConvertOutputElement,CreatedFile2,WrittenFile2,ProcessedFile2}
import com.overviewdocs.util.Logger

class StepOutputFragmentCollector(file2Writer: File2Writer, logicName: String) {
  def forParent(
    parentFile2: WrittenFile2
  )(implicit mat: Materializer): Flow[StepOutputFragment, ConvertOutputElement, akka.NotUsed] = {
    implicit val ec = mat.executionContext
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

    case class ScanResult(
      toEmit: Vector[ConvertOutputElement],
      state: State
    )

    def onFragment(lastEmitAndState: ScanResult, fragment: StepOutputFragment): Future[ScanResult] = {
      val state = lastEmitAndState.state
      (state, fragment) match {
        case (_, p: StepOutputFragment.Progress) => { parentFile2.onProgress(p.fraction); ignore(state) }
        case (End, _) => ignore(state) // we've marked the parent as processed: garbage can't change our minds

        // In edge cases, workers can send duplicate messages. The most common
        // is "restart", in which a second worker receives all the same
        // fragments as the first.
        case (AtChild(child), h: StepOutputFragment.File2Header) if h.indexInParent != child.indexInParent + 1 => ignore(state)
        case (AtChild(child), StepOutputFragment.Blob(i, _)) if i != child.indexInParent => ignore(state)
        case (AtChild(child), StepOutputFragment.Text(i, _)) if i != child.indexInParent => ignore(state)
        case (AtChild(child), StepOutputFragment.Thumbnail(i, _, _)) if i != child.indexInParent => ignore(state)

        case (Start, h: StepOutputFragment.File2Header) => createChild(None, h)
        case (Start, e: StepOutputFragment.EndFragment) => end(None, e)
        case (Start, f) => unexpectedFragment(f, None)
        case (AtChild(child), StepOutputFragment.Blob(_, stream)) => addBlob(child, stream)
        case (AtChild(child), StepOutputFragment.InheritBlob) => inheritBlob(child)
        case (AtChild(child), StepOutputFragment.Text(_, stream)) => addText(child, stream)
        case (AtChild(child), StepOutputFragment.Thumbnail(_, ct, stream)) => addThumbnail(child, ct, stream)
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
      error("logic error in " + logicName + ": " + message, currentChild)
    }

    def unexpectedFragment(f: StepOutputFragment, currentChild: Option[CreatedFile2]): Future[ScanResult] = {
      logicError("unexpected fragment " + f.getClass.toString, currentChild)
    }

    def writeLastChildOpt(lastChildOpt: Option[CreatedFile2]): Future[Vector[ConvertOutputElement]] = {
      lastChildOpt match {
        case Some(child) if child.pipelineOptions.stepsRemaining.isEmpty => {
          file2Writer.setWrittenAndProcessed(child).map(p => Vector(ConvertOutputElement.ToIngest(p)))
        }
        case Some(child) => {
          file2Writer.setWritten(child).map(w => Vector(ConvertOutputElement.ToProcess(w)))
        }
        case None => Future.successful(Vector.empty)
      }
    }

    def createChild(lastChild: Option[CreatedFile2], header: StepOutputFragment.File2Header): Future[ScanResult] = {
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
        } yield {
          ScanResult(lastChildWritten, AtChild(nextChild))
        }
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
          lastChildWritten: Vector[ConvertOutputElement] <- writeLastChildOpt(lastChildOpt)
          parentProcessed <- writeParent(lastChildOpt.map(_.indexInParent + 1).getOrElse(0), None)
        } yield ScanResult(lastChildWritten :+ parentProcessed, End)
      }
    }

    def writeParent(nChildren: Int, error: Option[String]): Future[ConvertOutputElement] = {
      file2Writer.setProcessed(parentFile2, nChildren, error).map(f => ConvertOutputElement.ToIngest(f))
    }

    Flow[StepOutputFragment]
      .scanAsync(ScanResult(Vector(), Start))(onFragment) // ScanResult
      .mapConcat(_.toEmit)                                // ConvertOutputElement
  }
}
