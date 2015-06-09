package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import scala.concurrent.Future

import org.overviewproject.database.BlockingDatabaseProvider
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._
import org.overviewproject.jobhandler.filegroup.JobDescription._
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
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
    progressReporter ! StartTask(documentSetId)
  }

  override def completeTask(task: TaskWorkerTask): Unit = {
    super.completeTask(task)
    progressReporter ! CompleteTask(documentSetId)

    if (jobStepComplete) {
      progressReporter ! CompleteJobStep(documentSetId)
      startNextJobStep(task)
    }
  }

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId)

  private def jobStepComplete = allTasksComplete && !jobCancelled

  /** Given the last task of the previous job step, kick off the next job step */
  private def startNextJobStep(task: TaskWorkerTask) =
    task match {
      case _: CreateSearchIndexAlias =>
        startJobStep(createDocumentsTasks, CreateDocumentsStepSize, ExtractText)
      case _: CreateDocuments =>
        startJobStep(completeDocumentSetTasks, CompleteDocumentSetStepSize, CreateDocument)
      case _: CompleteDocumentSet => finishJob
    }

  private def startJobStep(tasks: Set[TaskWorkerTask], stepSize: Double, description: JobDescription) = {
    taskQueue ! AddTasks(tasks)

    tasks.map(addTask)

    progressReporter ! StartJobStep(documentSetId, tasks.size, stepSize, description)
  }

  private def finishJob =  progressReporter ! CompleteJob(documentSetId)

  private def createDocumentsTasks: Set[TaskWorkerTask] = for {
    uploadedFileId <- uploadedFilesInFileGroup(fileGroupId)
  } yield CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier)

  private def completeDocumentSetTasks: Set[TaskWorkerTask] = Set(CompleteDocumentSet(documentSetId, fileGroupId))

  protected val storage: Storage
  
  protected trait Storage {
    def uploadedFileIds(fileGroupId: Long): Set[Long]
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

    protected class DatabaseStorage extends Storage with BlockingDatabaseProvider {
      import blockingDatabaseApi._
      import org.overviewproject.models.tables.GroupedFileUploads

      override def uploadedFileIds(fileGroupId: Long): Set[Long] = {
        blockingDatabase.seq(GroupedFileUploads.filter(_.fileGroupId === fileGroupId).map(_.id)).toSet
      }
    }
  }
}
