package com.overviewdocs.ingest.process

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Source}
import akka.util.ByteString
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.model.{ConvertOutputElement,CreatedFile2,WrittenFile2,ProcessedFile2,ProgressPiece}
import com.overviewdocs.models.File2
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
  * Call StepOutputFragmentCollector.transitionState directly to obtain a Future
  * that succeeds after the inner Source has been consumed.
  */
class StepOutputFragmentCollector(file2Writer: File2Writer, logicName: String, progressWeight: Double) {
  import StepOutputFragmentCollector.State
  import State._

  def initialStateForInput(parentFile2: WrittenFile2): State = {
    val (selfProgress, childrenProgress) = parentFile2.progressPiece.bisect(progressWeight)
    State.Start(Parent(parentFile2, selfProgress, childrenProgress))
  }

  def flowForParent(
    parentFile2: WrittenFile2
  )(implicit mat: Materializer): Flow[StepOutputFragment, ConvertOutputElement, akka.NotUsed] = {
    implicit val ec = mat.executionContext

    //.scanAsync does not complete for us, akka-streams 2.5.11.
    // Perhaps https://github.com/akka/akka/pull/24817 fixes it?
    // In the meantime, we'll use .mapAsync()+.mapConcat(identity)
    //val initialState = initialStateForInput(parentFile2)
    //Flow[StepOutputFragment]
    //  .scanAsync(initialState)((state, fragment) => transitionState(state, fragment))
    //  .mapConcat(_.toEmit) // ConvertOutputElement
    var hackyMutatingState = initialStateForInput(parentFile2)
    Flow[StepOutputFragment]
      .mapAsync(1) { fragment =>
        for {
          newState <- transitionState(hackyMutatingState, fragment)
        } yield {
          hackyMutatingState = newState
          hackyMutatingState.toEmit
        }
      }
      .mapConcat(identity _)
  }

  def transitionState(
    state: State,
    fragment: StepOutputFragment
  )(implicit mat: Materializer): Future[State] = {
    implicit val ec = mat.executionContext

    state match {
      case Start(parent) => {
        fragment match {
          // Progress reporting: call the callback; don't change state.
          case p: StepOutputFragment.Progress => {
            parent.selfProgress.report(Math.max(0.0, Math.min(1.0, p.fraction)))
            Future.successful(state)
          }

          case h: StepOutputFragment.File2Header => createChild(parent, None, h)
          case e: StepOutputFragment.EndFragment => end(parent, None, e)
          case f => unexpectedFragment(parent, f, None)
        }
      }

      case AtChild(parent, child, _) => {
        fragment match {
          // Progress reporting: call the callback; don't change state.
          case p: StepOutputFragment.Progress => {
            parent.selfProgress.report(Math.max(0.0, Math.min(1.0, p.fraction)))
            Future.successful(AtChild(parent, child.copy(childrenProgressRight=p.fraction), Nil))
          }

          // In edge cases, workers can send duplicate messages. The most common
          // such case is "restart", in which a second worker comes along and
          // re-generates all the fragments the first worker did.
          //
          // Ignore duplicate child fragments.
          //
          // (Note that we don't test for _missing_ fragments. We assume that
          // won't happen -- we'll only get duplicates.)
          case f: StepOutputFragment.File2Header if f.indexInParent < child.file.indexInParent + 1 => {
            Future.successful(AtChild(parent, child, Nil))
          }
          case f: StepOutputFragment.ChildFragment if f.indexInParent < child.file.indexInParent => {
            Future.successful(AtChild(parent, child, Nil))
          }

          case StepOutputFragment.Blob(_, stream) => addBlob(parent, child, stream)
          case StepOutputFragment.InheritBlob => inheritBlob(parent, child)
          case StepOutputFragment.Text(_, stream) => addText(parent, child, stream)
          case StepOutputFragment.Thumbnail(_, ct, stream) => addThumbnail(parent, child, ct, stream)
          case h: StepOutputFragment.File2Header => createChild(parent, Some(child), h)
          case e: StepOutputFragment.EndFragment => end(parent, Some(child), e)
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
    parent: Parent,
    message: String,
    currentChild: Option[Child]
  )(implicit ec: ExecutionContext): Future[State] = {
    currentChild match {
      case Some(child) => {
        // We'll delete the current child File2: it isn't WRITTEN
        val nPreviousChildren = child.file.indexInParent
        for {
          _ <- file2Writer.delete(child.file)
          parentProcessed <- writeParent(parent, nPreviousChildren, Some(message))
        } yield State.End(List(parentProcessed))
      }
      case None => {
        for {
          parentProcessed <- writeParent(parent, 0, Some(message))
        } yield State.End(List(parentProcessed))
      }
    }
  }

  private def logicError(
    parent: Parent,
    message: String,
    currentChild: Option[Child]
  )(implicit ec: ExecutionContext): Future[State] = {
    error(parent, "logic error in " + logicName + ": " + message, currentChild)
  }

  private def missingBlobError(
    parent: Parent,
    child: Child,
  )(implicit ec: ExecutionContext): Future[State] = {
    logicError(parent, "tried to write child without blob data", Some(child))
  }

  private def unexpectedFragment(
    parent: Parent,
    f: StepOutputFragment,
    currentChild: Option[Child]
  )(implicit ec: ExecutionContext): Future[State] = {
    logicError(parent, "unexpected fragment " + f.getClass.toString, currentChild)
  }

  private def writeLastChildOpt(
    parent: Parent,
    lastChildOpt: Option[Child]
  )(implicit ec: ExecutionContext): Future[List[ConvertOutputElement]] = {
    lastChildOpt match {
      case Some(child) => {
        val childProgress = parent.childProgressPiece(child)

        if (isChildAlreadyProcessed(child)) {
          file2Writer.setWrittenAndProcessed(child.file)
            .map { p =>
              childProgress.report(1.0)
              List(ConvertOutputElement.ToIngest(p))
            }
        } else {
          file2Writer.setWritten(child.file)
            .map(w => List(ConvertOutputElement.ToProcess(w.copy(progressPiece=childProgress))))
        }
      }
      case None => Future.successful(Nil)
    }
  }

  private def isChildAlreadyProcessed(child: Child): Boolean = {
    val file = child.file
    (
      file.contentType == "application/pdf"
      && !file.wantOcr
      && !file.wantSplitByPage
      && file.thumbnailLocationOpt.nonEmpty
      && file.blobOpt.nonEmpty
      // TODO: && file.hasText
    )
  }

  private def createChild(
    parent: Parent,
    lastChild: Option[Child],
    header: StepOutputFragment.File2Header
  )(implicit ec: ExecutionContext): Future[State] = {
    if (lastChild.map(_.file.blobOpt) == Some(None)) {
      missingBlobError(parent, lastChild.get)
    } else {
      for {
        lastChildWritten <- writeLastChildOpt(parent, lastChild)
        nextChild <- file2Writer.createChild(
          parent.file,
          lastChild.map(_.file.indexInParent + 1).getOrElse(0),
          header.filename,
          header.contentType,
          header.languageCode,
          header.metadata,
          header.wantOcr,
          header.wantSplitByPage
        )
      } yield State.AtChild(
        parent,
        Child(
          nextChild,
          lastChild.map(_.childrenProgressRight).getOrElse(0.0),
          lastChild.map(_.childrenProgressRight).getOrElse(0.0) // progress size 0, to start
        ),
        lastChildWritten
      )
    }
  }

  private def addBlob(
    parent: Parent,
    child: Child,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[State] = {
    for {
      writtenChild <- file2Writer.writeBlob(child.file, data)
    } yield State.AtChild(parent, child.copy(file=writtenChild), Nil)
  }

  private def addThumbnail(
    parent: Parent,
    child: Child,
    contentType: String,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[State] = {
    for {
      writtenChild <- file2Writer.writeThumbnail(child.file, contentType, data)
    } yield State.AtChild(parent, child.copy(file=writtenChild), Nil)
  }

  private def addText(
    parent: Parent,
    child: Child,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[State] = {
    for {
      writtenChild <- file2Writer.writeText(child.file, data)
    } yield State.AtChild(parent, child.copy(file=writtenChild), Nil)
  }

  private def inheritBlob(
    parent: Parent,
    child: Child
  )(implicit ec: ExecutionContext): Future[State] = {
    if (child.file.indexInParent != 0) {
      logicError(parent, "tried to inherit blob when indexInParent!=0", Some(child))
    } else {
      for {
        writtenChild <- file2Writer.writeBlobStorageRef(child.file, parent.file.blob)
      } yield State.AtChild(parent, child.copy(file=writtenChild), Nil)
    }
  }

  private def end(
    parent: Parent,
    lastChildOpt: Option[Child],
    fragment: StepOutputFragment.EndFragment
  )(implicit ec: ExecutionContext): Future[State] = {
    (lastChildOpt, fragment) match {
      case (_, StepOutputFragment.FileError(message)) => error(parent, message, lastChildOpt)
      case (_, StepOutputFragment.StepError(ex)) => error(parent, "step error: " + ex.getMessage, lastChildOpt)
      case (_, StepOutputFragment.Canceled) => error(parent, "canceled", lastChildOpt)
      case (Some(child), _) if (child.file.blobOpt.isEmpty) => missingBlobError(parent, child)
      case (_, StepOutputFragment.Done) => for {
        lastChildWritten: List[ConvertOutputElement] <- writeLastChildOpt(parent, lastChildOpt.map(_.copy(childrenProgressRight=1.0)))
        parentProcessed <- writeParent(parent, lastChildOpt.map(_.file.indexInParent + 1).getOrElse(0), None)
      } yield State.End(lastChildWritten :+ parentProcessed)
    }
  }

  private def writeParent(
    parent: Parent,
    nChildren: Int,
    error: Option[String]
  )(implicit ec: ExecutionContext): Future[ConvertOutputElement] = {
    for {
      f <- file2Writer.setProcessed(parent.file, nChildren, error)
    } yield {
      if (nChildren == 0) {
        parent.file.progressPiece.report(1.0)
      } else {
        parent.selfProgress.report(1.0)
      }
      ConvertOutputElement.ToIngest(f)
    }
  }
}

object StepOutputFragmentCollector {
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
      * val state: State = initialStateForInput(parentFile2)
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
      parent: Parent
    ) extends State {
      override val toEmit = Nil
    }

    case class Parent(
      file: WrittenFile2,
      selfProgress: ProgressPiece,
      childrenProgress: ProgressPiece
    ) {
      def childProgressPiece(child: Child): ProgressPiece = {
        childrenProgress.slice(child.childrenProgressLeft, child.childrenProgressRight)
      }
    }

    case class Child(
      file: CreatedFile2,
      childrenProgressLeft: Double,
      childrenProgressRight: Double
    )

    case class AtChild(
      parent: Parent,
      child: Child,
      override val toEmit: List[ConvertOutputElement]
    ) extends State

    case class End(
      override val toEmit: List[ConvertOutputElement]
    ) extends State
  }
}
