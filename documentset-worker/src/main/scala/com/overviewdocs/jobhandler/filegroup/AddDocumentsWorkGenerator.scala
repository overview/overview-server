package com.overviewdocs.jobhandler.filegroup

import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.messages.DocumentSetCommands.AddDocumentsFromFileGroup
import com.overviewdocs.models.{FileGroup,GroupedFileUpload}
import com.overviewdocs.models.tables.GroupedFileUploads

/** Creates Work objects, tracking command state.
  *
  * Use it like this:
  *
  *     // scheduler
  *     val fileGroup: FileGroup = ...
  *     val uploads: Seq[GroupedFileUpload] = ...
  *     val generator = new AddDocumentsWorkGenerator(fileGroup, uploads)
  *
  *     // when a worker comes along
  *     generator.nextWork match {
  *       case ProcessFileWork(upload) =&gt; doSomething(fileGroup, upload).andThen { case _ =&gt; generator.markDoneOne }
  *       case FinishJobWork =&gt; doSomething(fileGroup) // and never call `nextWork()` again; `generator` is done
  *       case NoWorkForNow =&gt; // do nothing, but try again after the next `generator.markDone()`
  *     }
  *
  * So there are two main aspects to the caller:
  *
  * 1. Something handles `ProcessFileWork` and `FinishJobWork` objects to
  *    modify the database. (This is probably a fleet of concurrent Actors.)
  * 2. Something schedules work: it gives jobs to Actors when there are jobs,
  *    and it makes them wait when there aren't.
  *
  * This class is not thread-safe. Use it from one thread at a time. (The
  * caller must wait for a response from `nextWork()` before it can decide
  * whether to call it again.)
  */
class AddDocumentsWorkGenerator(
  val fileGroup: FileGroup,
  val uploads: Seq[GroupedFileUpload]
) {
  @volatile var remainingUploads = uploads
  @volatile var nInProgress = 0

  /** Requests another Work to act upon. */
  def nextWork: AddDocumentsWorkGenerator.Work = {
    if (remainingUploads.nonEmpty) {
      val upload = remainingUploads.head
      remainingUploads = remainingUploads.tail
      nInProgress += 1
      AddDocumentsWorkGenerator.ProcessFileWork(upload)
    } else if (nInProgress > 0) {
      AddDocumentsWorkGenerator.NoWorkForNow
    } else {
      AddDocumentsWorkGenerator.FinishJobWork
    }
  }

  /** Skips all remaining GroupedFileUploads.
    *
    * This is called during cancellation, so we can get right to the
    * FinishJobWork.
    */
  def skipRemainingFileWork: Unit = remainingUploads = Seq()

  /** Reports that a unit of Work returned from `nextWork` was completed.
    *
    * If a previous call to `nextWork` returned `NoWorkForNow`, it might return
    * something different after this call.
    */
  def markDoneOne: Unit = {
    if (nInProgress <= 0) throw new AssertionError("nInProgress <= 0")
    nInProgress -= 1
  }
}

object AddDocumentsWorkGenerator extends HasDatabase {
  /** A packet of work to be done by a worker.
    */
  sealed trait Work

  case class ProcessFileWork(upload: GroupedFileUpload) extends Work
  case object FinishJobWork extends Work
  case object NoWorkForNow extends Work

  def loadForCommand(command: AddDocumentsFromFileGroup)(implicit ec: ExecutionContext): Future[AddDocumentsWorkGenerator] = {
    import database.api._

    for {
      uploads <- database.seq(GroupedFileUploads.filter(_.fileGroupId === command.fileGroup.id))
    } yield new AddDocumentsWorkGenerator(command.fileGroup, uploads)
  }
}
