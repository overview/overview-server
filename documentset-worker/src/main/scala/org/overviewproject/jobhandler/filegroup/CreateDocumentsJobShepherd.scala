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

  protected val NumberOfJobSteps = 3
  protected val CreateAliasStepSize = 0.05
  protected val CreateDocumentsStepSize = 0.90
  protected val CompleteDocumentSetStepSize = 0.05

  override protected def generateTasks: Iterable[TaskWorkerTask] = {
    val tasks = Set(CreateSearchIndexAlias(documentSetId, fileGroupId))

    progressReporter ! StartJob(documentSetId, NumberOfJobSteps, ProcessUpload)
    progressReporter ! StartJobStep(documentSetId, tasks.size, CreateAliasStepSize, ExtractText)

    taskQueue ! AddTasks(tasks)
    tasks
  }

  // TODO: we need a unified progress reporting mechanism, but for now, do this ugly thing,
  // since progress reporting only applies to these tasks.

  override def startTask(task: TaskWorkerTask): Unit = {
    super.startTask(task)
    task match {
      case CreateSearchIndexAlias(_, _) => {
        progressReporter ! StartTask(documentSetId)
      }
      case CreateDocuments(_, _, uploadedFileId, _, _) =>
        progressReporter ! StartTask(documentSetId)
      case CompleteDocumentSet(_, _) =>
        progressReporter ! StartTask(documentSetId)
      case _ =>
    }
  }

  override def completeTask(task: TaskWorkerTask): Unit = {
    super.completeTask(task)
    task match {
      case CreateSearchIndexAlias(_, _) => {
        progressReporter ! CompleteTask(documentSetId)

        if (jobStepComplete) startNextJobStep(
          createDocumentsTasks,
          CreateDocumentsStepSize,
          ExtractText)
      }
      case CreateDocuments(_, _, uploadedFileId, _, _) => {
        progressReporter ! CompleteTask(documentSetId)

        if (jobStepComplete) startNextJobStep(
          completeDocumentSetTasks,
          CompleteDocumentSetStepSize,
          CreateDocument)
      }
      case CompleteDocumentSet(documentSetId, _) => {
        progressReporter ! CompleteTask(documentSetId)

        if (jobStepComplete) finishJob
      }
      case _ =>
    }

  }

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId)

  private def jobStepComplete = allTasksComplete && !jobCancelled

  
  private def startNextJobStep(tasks: Set[TaskWorkerTask], stepSize: Double, description: JobDescription) = {
    progressReporter ! CompleteJobStep(documentSetId)

    taskQueue ! AddTasks(tasks)

    tasks.map(addTask)

    progressReporter ! StartJobStep(documentSetId, tasks.size, stepSize, description)
  }
  

  private def finishJob = {
    progressReporter ! CompleteJobStep(documentSetId)
    progressReporter ! CompleteJob(documentSetId)
  }

  private def createDocumentsTasks: Set[TaskWorkerTask] = for {
    uploadedFileId <- uploadedFilesInFileGroup(fileGroupId)
  } yield CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier)

  private def completeDocumentSetTasks: Set[TaskWorkerTask] = Set(CompleteDocumentSet(documentSetId, fileGroupId))

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
