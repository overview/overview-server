package com.overviewdocs.ingest.pipeline

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Source}
import akka.util.ByteString
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.models.{ConvertOutputElement,CreatedFile2,WrittenFile2,ProcessedFile2}
import com.overviewdocs.util.Logger

/** Feeds input StepOutputFragments to file2Writer and emits resulting
  * ConvertOutputElements.
  *
  * Each fragment is passed to a (side-effect producing) File2Writer
  * method; output elements are emitted as soon as they're ready. After
  * receiving an EndFragment, the input parentFile2 is written and emitted,
  * its state changed to "Processed."
  *
  * Beware: if you connect a Source of StepOutputFragments, the fact that
  * StepOutputFragmentCollector has _consumed_ the fragment doesn't mean it's
  * finished with it. That's because some fragments themselves contain Sources.
  * Use StepOutputFragmentCollector.transitionState for that.
  */
class StepOutputFragmentCollector(file2Writer: File2Writer, logicName: String) {
  def flowForParent(
    parentFile2: WrittenFile2
  )(implicit mat: Materializer): Flow[StepOutputFragment, ConvertOutputElement, akka.NotUsed] = {
    implicit val ec = mat.executionContext

    val initialState: State = State.Start(parentFile2)

    Flow[StepOutputFragment]
      .scanAsync(initialState)((state, fragment) => transitionState(state, fragment))
      .mapConcat(_.toEmit)                                // ConvertOutputElement
  }

  sealed trait State {
    /** Holds information for a `Flow.scanAsync()` pattern.
      *
      * akka-stream doesn't have exactly what we want. Its foldAsync() is great
      * for generating side-effects and maintaining state, but it doesn't emit
      * anything. scanAsync() is great for emitting, but it emits exactly its
      * internal state, which is too much information.
      *
      * This State is meant to be run through scanAsync() and then decomposed to
      * only its useful information:
      *
      * val state: State = State.Start(parentFile2)
      * sourceOfFragments      // Source[StepOutputFragment, _]
      *   .scanAsync { (state, fragment) =>
      *     transitionState(state, fragment)
      *   }                    // Source[State, _]
      *   .mapConcat(_.toEmit) // Source[ConvertOutputElement, _]
      *
      * Each toEmit holds zero, one or two ConvertOutputElements.
      */
    val toEmit: List[ConvertOutputElement]
  }
  object State {
    case class Start(
      parentFile2: WrittenFile2
    ) extends State {
      override val toEmit = Nil
    }

    case class AtChild(
      parentFile2: WrittenFile2,
      child: CreatedFile2,
      override val toEmit: List[ConvertOutputElement]
    ) extends State

    case class End(
      override val toEmit: List[ConvertOutputElement]
    ) extends State
  }

  def transitionState(
    state: State,
    fragment: StepOutputFragment
  )(implicit ec: ExecutionContext, mat: Materializer): Future[State] = {
    import State._

    state match {
      case Start(parentFile2) => {
        fragment match {
          // Progress reporting: call the callback; don't change state.
          case p: StepOutputFragment.Progress => {
            parentFile2.onProgress(p.fraction)
            Future.successful(state)
          }

          case h: StepOutputFragment.File2Header => createChild(parentFile2, None, h)
          case e: StepOutputFragment.EndFragment => end(parentFile2, None, e)
          case f => unexpectedFragment(parentFile2, f, None)
        }
      }

      case AtChild(parentFile2, child, _) => {
        fragment match {
          // Progress reporting: call the callback; don't change state.
          case p: StepOutputFragment.Progress => {
            parentFile2.onProgress(p.fraction)
            Future.successful(AtChild(parentFile2, child, Nil))
          }

          // In edge cases, workers can send duplicate messages. The most common
          // such case is "restart", in which a second worker comes along and
          // re-generates all the fragments the first worker did.
          //
          // Ignore duplicate child fragments.
          //
          // (Note that we don't test for _missing_ fragments. We assume that
          // won't happen -- we'll only get duplicates.)
          case f: StepOutputFragment.File2Header if f.indexInParent < child.indexInParent + 1 => {
            Future.successful(AtChild(parentFile2, child, Nil))
          }
          case f: StepOutputFragment.ChildFragment if f.indexInParent < child.indexInParent => {
            Future.successful(AtChild(parentFile2, child, Nil))
          }

          case StepOutputFragment.Blob(_, stream) => addBlob(parentFile2, child, stream)
          case StepOutputFragment.InheritBlob => inheritBlob(parentFile2, child)
          case StepOutputFragment.Text(_, stream) => addText(parentFile2, child, stream)
          case StepOutputFragment.Thumbnail(_, ct, stream) => addThumbnail(parentFile2, child, ct, stream)
          case h: StepOutputFragment.File2Header => createChild(parentFile2, Some(child), h)
          case e: StepOutputFragment.EndFragment => end(parentFile2, Some(child), e)
        }
      }

      case End(_) => {
        // Post-End fragments: ignore them, since we already emitted a
        // parentFile2 as "Processed".
        Future.successful(End(Nil))
      }
    }
  }

  private def error(
    parentFile2: WrittenFile2,
    message: String,
    currentChild: Option[CreatedFile2]
  )(implicit ec: ExecutionContext): Future[State] = {
    currentChild match {
      case Some(child) => {
        // We'll delete the current child File2: it isn't WRITTEN
        val nPreviousChildren = child.indexInParent
        for {
          _ <- file2Writer.delete(child)
          parentProcessed <- writeParent(parentFile2, nPreviousChildren, Some(message))
        } yield State.End(List(parentProcessed))
      }
      case None => {
        for {
          parentProcessed <- writeParent(parentFile2, 0, Some(message))
        } yield State.End(List(parentProcessed))
      }
    }
  }

  private def logicError(
    parentFile2: WrittenFile2,
    message: String,
    currentChild: Option[CreatedFile2]
  )(implicit ec: ExecutionContext): Future[State] = {
    error(parentFile2, "logic error in " + logicName + ": " + message, currentChild)
  }

  private def missingBlobError(
    parentFile2: WrittenFile2,
    child: CreatedFile2
  )(implicit ec: ExecutionContext): Future[State] = {
    logicError(parentFile2, "tried to write child without blob data", Some(child))
  }

  private def unexpectedFragment(
    parentFile2: WrittenFile2,
    f: StepOutputFragment,
    currentChild: Option[CreatedFile2]
  )(implicit ec: ExecutionContext): Future[State] = {
    logicError(parentFile2, "unexpected fragment " + f.getClass.toString, currentChild)
  }

  private def writeLastChildOpt(
    lastChildOpt: Option[CreatedFile2]
  )(implicit ec: ExecutionContext): Future[List[ConvertOutputElement]] = {
    lastChildOpt match {
      case Some(child) => {
        if (child.pipelineOptions.stepsRemaining.isEmpty && child.contentType == "application/pdf") {
          file2Writer.setWrittenAndProcessed(child).map(p => List(ConvertOutputElement.ToIngest(p)))
        } else {
          file2Writer.setWritten(child).map(w => List(ConvertOutputElement.ToProcess(w)))
        }
      }
      case None => Future.successful(Nil)
    }
  }

  private def createChild(
    parentFile2: WrittenFile2,
    lastChild: Option[CreatedFile2],
    header: StepOutputFragment.File2Header
  )(implicit ec: ExecutionContext): Future[State] = {
    if (lastChild.map(_.blobOpt) == Some(None)) {
      missingBlobError(parentFile2, lastChild.get)
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
      } yield State.AtChild(parentFile2, nextChild, lastChildWritten)
    }
  }

  private def addBlob(
    parentFile2: WrittenFile2,
    child: CreatedFile2,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[State] = {
    for {
      writtenChild <- file2Writer.writeBlob(child, data)
    } yield State.AtChild(parentFile2, writtenChild, Nil)
  }

  private def addThumbnail(
    parentFile2: WrittenFile2,
    child: CreatedFile2,
    contentType: String,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[State] = {
    for {
      writtenChild <- file2Writer.writeThumbnail(child, contentType, data)
    } yield State.AtChild(parentFile2, writtenChild, Nil)
  }

  private def addText(
    parentFile2: WrittenFile2,
    child: CreatedFile2,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[State] = {
    for {
      writtenChild <- file2Writer.writeText(child, data)
    } yield State.AtChild(parentFile2, writtenChild, Nil)
  }

  private def inheritBlob(
    parentFile2: WrittenFile2,
    child: CreatedFile2
  )(implicit ec: ExecutionContext): Future[State] = {
    if (child.indexInParent != 0) {
      logicError(parentFile2, "tried to inherit blob when indexInParent!=0", Some(child))
    } else {
      for {
        writtenChild <- file2Writer.writeBlobStorageRef(child, parentFile2.blob)
      } yield State.AtChild(parentFile2, writtenChild, Nil)
    }
  }

  private def end(
    parentFile2: WrittenFile2,
    lastChildOpt: Option[CreatedFile2],
    fragment: StepOutputFragment.EndFragment
  )(implicit ec: ExecutionContext): Future[State] = {
    (lastChildOpt, fragment) match {
      case (_, StepOutputFragment.FileError(message)) => error(parentFile2, message, lastChildOpt)
      case (_, StepOutputFragment.StepError(ex)) => error(parentFile2, "step error: " + ex.getMessage, lastChildOpt)
      case (_, StepOutputFragment.Canceled) => error(parentFile2, "canceled", lastChildOpt)
      case (Some(child), _) if (child.blobOpt.isEmpty) => missingBlobError(parentFile2, child)
      case (_, StepOutputFragment.Done) => for {
        lastChildWritten: List[ConvertOutputElement] <- writeLastChildOpt(lastChildOpt)
        parentProcessed <- writeParent(parentFile2, lastChildOpt.map(_.indexInParent + 1).getOrElse(0), None)
      } yield State.End(lastChildWritten :+ parentProcessed)
    }
  }

  private def writeParent(
    parentFile2: WrittenFile2,
    nChildren: Int,
    error: Option[String]
  )(implicit ec: ExecutionContext): Future[ConvertOutputElement] = {
    file2Writer.setProcessed(parentFile2, nChildren, error)
      .map(f => ConvertOutputElement.ToIngest(f))
  }
}
