package com.overviewdocs.jobhandler.filegroup

import java.time.Instant
import scala.collection.mutable
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
  private case class UploadInfo(fractionComplete: Double)

  private val millisAtStart: Long = Instant.now.toEpochMilli
  private val nBytesUnfinishedAtStart: Long = uploads.map(_.size).sum
  private var nBytesUnfinished: Long = nBytesUnfinishedAtStart
  private var nFilesUnfinished: Int = uploads.length

  private val pendingUploads: Iterator[GroupedFileUpload] = if (fileGroup.deleted) Iterator() else uploads.iterator
  private val inProgress: mutable.Map[GroupedFileUpload,UploadInfo] = mutable.Map()

  /** Requests another Work to act upon. */
  def nextWork: AddDocumentsWorkGenerator.Work = {
    if (pendingUploads.hasNext) {
      val upload = pendingUploads.next
      inProgress(upload) = UploadInfo(0.0)
      AddDocumentsWorkGenerator.ProcessFileWork(upload)
    } else if (inProgress.nonEmpty) {
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
  def skipRemainingFileWork: Unit = {
    while (pendingUploads.hasNext) pendingUploads.next
    nFilesUnfinished = 0
    nBytesUnfinished = 0
  }

  /** Tell the generator a unit of Work returned from `nextWork` was completed.
    *
    * If a previous call to `nextWork` returned `NoWorkForNow`, it might return
    * something different after this call.
    */
  def markWorkDone(upload: GroupedFileUpload): Unit = {
    nFilesUnfinished -= 1
    nBytesUnfinished -= upload.size
    assert(inProgress.contains(upload))
    inProgress.-=(upload)
  }

  /** The current progress.
    *
    * This works even when `fileGroup.nFiles != uploads.length`, which happens
    * when resuming across restarts.
    *
    * The return value gives an estimated end time. That date is calculated
    * by extrapolating from now. If no bytes have been processed yet, it will
    * be Instant.MAX.
    */
  def progress: AddDocumentsWorkGenerator.Progress = {
    val nBytesInCurrentUploads = inProgress.toSeq
      .map { case (upload, info) => (upload.size * info.fractionComplete).toLong }
      .sum

    val nFiles = fileGroup.nFiles.get - nFilesUnfinished
    val nBytes = fileGroup.nBytes.get - nBytesUnfinished + nBytesInCurrentUploads

    val estimatedCompletionTime = if (nBytesUnfinished != nBytesUnfinishedAtStart) {
      val fractionRemaining: Double = nBytesUnfinished.toDouble / nBytesUnfinishedAtStart
      val fractionComplete: Double = 1.0 - fractionRemaining
      val millisDone = Instant.now.toEpochMilli - millisAtStart
      val estimatedMillis = math.ceil(millisDone / fractionComplete).toLong
      Instant.ofEpochMilli(millisAtStart + estimatedMillis)
    } else {
      Instant.MAX
    }

    AddDocumentsWorkGenerator.Progress(nFiles, nBytes, estimatedCompletionTime)
  }

  /** Tell the generator an upload is partially processed.
    *
    * @param upload The upload, which was returned by nextWork and hasn't been
    *               passed to markWorkDone.
    * @param fractionComplete A number between 0 (not started) and 1 (done).
    */
  def markWorkProgress(upload: GroupedFileUpload, fractionComplete: Double): Unit = {
    assert(fractionComplete >= 0.0)
    assert(fractionComplete <= 1.0)
    inProgress(upload) = UploadInfo(fractionComplete)
  }
}

object AddDocumentsWorkGenerator extends HasDatabase {
  /** A packet of work to be done by a worker.
    */
  sealed trait Work

  case class ProcessFileWork(upload: GroupedFileUpload) extends Work
  case object FinishJobWork extends Work
  case object NoWorkForNow extends Work

  case class Progress(nFilesProcessed: Int, nBytesProcessed: Long, estimatedCompletionTime: Instant)

  def loadForCommand(command: AddDocumentsFromFileGroup)(implicit ec: ExecutionContext): Future[AddDocumentsWorkGenerator] = {
    import database.api._

    for {
      uploads <- database.seq(GroupedFileUploads.filter(_.fileGroupId === command.fileGroup.id))
    } yield new AddDocumentsWorkGenerator(command.fileGroup, uploads)
  }
}
