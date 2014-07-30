package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder

trait CreateDocumentsJobTracker extends JobTracker {
  val documentSetId: Long
  val fileGroupId: Long
  val taskQueue: ActorRef
  val progressReporter: ActorRef

  override protected def generateTasks: Iterable[TaskWorkerTask] = {
    val tasks = uploadedFilesInFileGroup(fileGroupId).map(CreatePagesTask(documentSetId, fileGroupId, _))

    progressReporter ! StartJob(documentSetId, tasks.size)

    taskQueue ! AddTasks(tasks)
    tasks
  }

  // TODO: we need a unified progress reporting mechanism, but for now, do this ugly thing,
  // since progress reporting only applies to these tasks.
  // Risk the MatchError because this tracker should only get one type of tasks
  override def startTask(task: TaskWorkerTask): Unit = {
    super.startTask(task)
    task match {
      case CreatePagesTask(documentSetId, fileGroupId, uploadedFileId) =>
        progressReporter ! StartTask(documentSetId, uploadedFileId)
    }
  }
  override def completeTask(task: TaskWorkerTask): Unit = {
    super.completeTask(task)
    task match {
      case CreatePagesTask(documentSetId, fileGroupId, uploadedFileId) =>
        progressReporter ! CompleteTask(documentSetId, uploadedFileId)
    }
  }

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId)

  protected val storage: Storage
  protected trait Storage {
    def uploadedFileIds(fileGroupId: Long): Set[Long]
  }
}

object CreateDocumentsJobTracker {
  def apply(documentSetId: Long, fileGroupId: Long, taskQueue: ActorRef, progressReporter: ActorRef): CreateDocumentsJobTracker =
    new CreateDocumentsJobTrackerImpl(documentSetId, fileGroupId, taskQueue, progressReporter)

  private class CreateDocumentsJobTrackerImpl(
      val documentSetId: Long,
      val fileGroupId: Long,
      val taskQueue: ActorRef,
      val progressReporter: ActorRef) extends CreateDocumentsJobTracker {

    override protected val storage = new DatabaseStorage

    protected class DatabaseStorage extends Storage {
      override def uploadedFileIds(fileGroupId: Long): Set[Long] = Database.inTransaction {
        GroupedFileUploadFinder.byFileGroup(fileGroupId).toIds.toSet
      }

    }
  }
}
