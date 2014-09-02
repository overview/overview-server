package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import org.overviewproject.database.orm.finders.FileFinder
import org.overviewproject.jobhandler.filegroup.JobDescription._

/**
 * Creates the tasks for generating `Document`s from uploaded files.
 */
trait CreateDocumentsJobShepherd extends JobShepherd {
  val documentSetId: Long
  val fileGroupId: Long
  val splitDocuments: Boolean
  val taskQueue: ActorRef
  val progressReporter: ActorRef

  val NumberOfJobSteps = 2
  val ExtractTextStepSize = 0.75
  val CreateDocumentsStepSize = 0.25
  val jobDescription = ExtractText
  
  override protected def generateTasks: Iterable[TaskWorkerTask] = {
    val tasks = uploadedFilesInFileGroup(fileGroupId).map(CreatePagesTask(documentSetId, fileGroupId, _))

    progressReporter ! StartJob(documentSetId, NumberOfJobSteps, jobDescription)
    progressReporter ! StartJobStep(documentSetId, tasks.size, ExtractTextStepSize, jobDescription)

    taskQueue ! AddTasks(tasks)
    tasks
  }

  // TODO: we need a unified progress reporting mechanism, but for now, do this ugly thing,
  // since progress reporting only applies to these tasks.
  // Risk the MatchError because this shepherd should only get known tasks
  override def startTask(task: TaskWorkerTask): Unit = {
    super.startTask(task)
    task match {
      case CreatePagesTask(documentSetId, fileGroupId, uploadedFileId) =>
        progressReporter ! StartTask(documentSetId, uploadedFileId)
      case _ =>
    }
  }
  override def completeTask(task: TaskWorkerTask): Unit = {
    super.completeTask(task)
    task match {
      case CreatePagesTask(documentSetId, fileGroupId, uploadedFileId) => {
        progressReporter ! CompleteTask(documentSetId, uploadedFileId)
        if (allTasksComplete && !jobCancelled) {
          progressReporter ! CompleteJobStep(documentSetId)
          
          val numberOfFiles = storage.processedFileCount(documentSetId).toInt
          
          progressReporter ! StartJobStep(documentSetId, numberOfFiles, CreateDocumentsStepSize, jobDescription)
          
          val createDocumentsTask = CreateDocumentsTask(documentSetId, fileGroupId, splitDocuments)     
          taskQueue ! AddTasks(Set(createDocumentsTask))
          addTask(createDocumentsTask)
        }
      }
      case CreateDocumentsTask(documentSetId, fileGroupId, splitDocuments) => 
        progressReporter ! CompleteJobStep(documentSetId)
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
  def apply(documentSetId: Long, fileGroupId: Long, splitDocuments: Boolean, taskQueue: ActorRef, progressReporter: ActorRef): CreateDocumentsJobShepherd =
    new CreateDocumentsJobShepherdImpl(documentSetId, fileGroupId, splitDocuments, taskQueue, progressReporter)

  private class CreateDocumentsJobShepherdImpl(
      val documentSetId: Long,
      val fileGroupId: Long,
      val splitDocuments: Boolean,
      val taskQueue: ActorRef,
      val progressReporter: ActorRef) extends CreateDocumentsJobShepherd {

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
