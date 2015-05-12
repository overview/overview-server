package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import org.overviewproject.database.orm.finders.FileFinder
import org.overviewproject.jobhandler.filegroup.JobDescription._
import org.overviewproject.jobhandler.filegroup.task.UploadProcessOptions
import org.overviewproject.jobhandler.filegroup.task.UploadProcessOptions

/**
 * Creates the tasks for generating `Document`s from uploaded files.
 */
trait CreateDocumentsJobShepherd extends JobShepherd {
  protected val documentSetId: Long
  protected val fileGroupId: Long
  protected val options: UploadProcessOptions
  protected val taskQueue: ActorRef
  protected val progressReporter: ActorRef
  protected val documentIdSupplier: ActorRef

  override protected def generateTasks: Iterable[TaskWorkerTask] = {
    val tasks = Set(CreateSearchIndexAlias(documentSetId, fileGroupId))

    progressReporter ! StartJob(documentSetId, 3, ProcessUpload)
    progressReporter ! StartJobStep(documentSetId, tasks.size, 0.05, ExtractText)

    taskQueue ! AddTasks(tasks)
    tasks
  }

  // TODO: we need a unified progress reporting mechanism, but for now, do this ugly thing,
  // since progress reporting only applies to these tasks.

  override def startTask(task: TaskWorkerTask): Unit = {
    super.startTask(task)
    task match {
      case CreateSearchIndexAlias(_, _) => {
        progressReporter ! StartTask(documentSetId, documentSetId)
      }
      case CreateDocuments(_, _, uploadedFileId, _, _) =>
        progressReporter ! StartTask(documentSetId, uploadedFileId)
      case CompleteDocumentSet(_, _) =>
        progressReporter ! StartTask(documentSetId, documentSetId)
      case _ =>
    }
  }

  override def completeTask(task: TaskWorkerTask): Unit = {
    super.completeTask(task)
    task match {
      case CreateSearchIndexAlias(_, _) => {
        progressReporter ! CompleteTask(documentSetId, documentSetId)
        if (allTasksComplete && !jobCancelled) {
          progressReporter ! CompleteJobStep(documentSetId)
          
          val tasks = uploadedFilesInFileGroup(fileGroupId).map(
            CreateDocuments(documentSetId, fileGroupId, _, options, documentIdSupplier))

          taskQueue ! AddTasks(tasks)
          
          tasks.map(addTask)
          
          progressReporter ! StartJobStep(documentSetId, tasks.size, 0.90, ExtractText)
        }
      }
      case CreateDocuments(_, _, uploadedFileId, _, _) => {
        progressReporter ! CompleteTask(documentSetId, uploadedFileId)
        if (allTasksComplete && !jobCancelled) {
          progressReporter ! CompleteJobStep(documentSetId)

          val tasks = Set(CompleteDocumentSet(documentSetId, fileGroupId))
          taskQueue ! AddTasks(tasks)
          
          tasks.map(addTask)
          
          progressReporter ! StartJobStep(documentSetId, 1, 0.05, CreateDocument)
        }
      }
      case CompleteDocumentSet(documentSetId, _) => {
        progressReporter ! CompleteTask(documentSetId, documentSetId)
        progressReporter ! CompleteJobStep(documentSetId)
        if (allTasksComplete && !jobCancelled) progressReporter ! CompleteJob(documentSetId)
      }
      case _ =>
    }

  }

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId)

  protected val storage: Storage
  protected trait Storage {
    def uploadedFileIds(fileGroupId: Long): Set[Long]
    def processedFileCount(documentSetId: Long): Long
  }
}

object CreateDocumentsJobShepherd {
  def apply(documentSetId: Long, fileGroupId: Long, options: UploadProcessOptions,
            taskQueue: ActorRef, progressReporter: ActorRef, documentIdSupplier: ActorRef): CreateDocumentsJobShepherd =
    new CreateDocumentsJobShepherdImpl(documentSetId, fileGroupId, options,
      taskQueue, progressReporter, documentIdSupplier)

  private class CreateDocumentsJobShepherdImpl(
    override protected val documentSetId: Long,
    override protected val fileGroupId: Long,
    override protected val options: UploadProcessOptions,
    override protected val taskQueue: ActorRef,
    override protected val progressReporter: ActorRef,
    override protected val documentIdSupplier: ActorRef) extends CreateDocumentsJobShepherd {

    override protected val storage = new DatabaseStorage

    protected class DatabaseStorage extends Storage {
      override def uploadedFileIds(fileGroupId: Long): Set[Long] = Database.inTransaction {
        GroupedFileUploadFinder.byFileGroup(fileGroupId).toIds.toSet
      }

      override def processedFileCount(documentSetId: Long): Long = Database.inTransaction {
        FileFinder.byDocumentSet(documentSetId).count

      }
    }
  }
}
