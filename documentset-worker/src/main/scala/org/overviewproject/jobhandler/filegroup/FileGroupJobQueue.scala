package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import akka.actor.Actor
import akka.actor.ActorRef
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import akka.actor.Props
import org.overviewproject.util.Logger
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._

object FileGroupJobQueueProtocol {
  case class CreateDocumentsFromFileGroup(documentSetId: Long, fileGroupId: Long)
  case class FileGroupDocumentsCreated(documentSetId: Long)
  case class CancelFileUpload(documentSetId: Long, fileGroupId: Long)
  case class DeleteFileUpload(documentSetId: Long, fileGroupId: Long)
  case class FileUploadDeleted(documentSetId: Long, fileGroupId: Long)
}

trait FileGroupJobQueue extends Actor {
  import FileGroupJobQueueProtocol._
  import FileGroupTaskWorkerProtocol._

  type DocumentSetId = Long

  protected val storage: Storage

  trait Storage {
    def uploadedFileIds(fileGroupId: Long): Set[Long]
  }

  protected val progressReporter: ActorRef

  private case class JobRequest(requester: ActorRef)

  private val workerPool: mutable.Set[ActorRef] = mutable.Set.empty
  private val taskQueue: mutable.Queue[TaskWorkerTask] = mutable.Queue.empty
  private val jobTasks: mutable.Map[DocumentSetId, Set[Long]] = mutable.Map.empty
  private val jobRequests: mutable.Map[DocumentSetId, JobRequest] = mutable.Map.empty
  private val busyWorkers: mutable.Map[ActorRef, TaskWorkerTask] = mutable.Map.empty

  def receive = {
    case RegisterWorker(worker) => {
      Logger.info(s"Registering worker ${worker.path.toString}")
      workerPool += worker
      if (!taskQueue.isEmpty) worker ! TaskAvailable

    }
    case CreateDocumentsFromFileGroup(documentSetId, fileGroupId) => {
      Logger.info(s"Extract text task for FileGroup [$fileGroupId]")
      if (isNewRequest(documentSetId)) {
        val fileIds = uploadedFilesInFileGroup(fileGroupId)

        progressReporter ! StartJob(documentSetId, fileIds.size)

        addNewTasksToQueue(documentSetId, fileGroupId, fileIds)
        jobRequests += (documentSetId -> JobRequest(sender))

        workerPool.map(_ ! TaskAvailable)
      }
    }
    case ReadyForTask => {
      if (!taskQueue.isEmpty) {
        taskQueue.dequeue match {
          case task @ CreatePagesTask(documentSetId, fileGroupId, uploadedFileId) => {
            Logger.info(s"Sending task $uploadedFileId to ${sender.path.toString}")
            progressReporter ! StartTask(documentSetId, uploadedFileId)
            sender ! task
            busyWorkers += (sender -> task)
          }
          case task @ DeleteFileUploadJob(documentSetId, fileGroupId) => {
            Logger.info(s"Sending delete job for $fileGroupId to ${sender.path.toString}")
            sender ! task
            busyWorkers += (sender -> task)
          } 
        }
      }
    }
    case CreatePagesTaskDone(documentSetId, fileGroupId, uploadedFileId) => {
      Logger.info(s"Task ${uploadedFileId} Done [$fileGroupId]")
      progressReporter ! CompleteTask(documentSetId, uploadedFileId)
      busyWorkers -= sender

      whenTaskIsComplete(documentSetId, fileGroupId, uploadedFileId) {
        notifyRequesterIfJobIsDone
      }
    }
    case CancelFileUpload(documentSetId, fileGroupId) => {
      Logger.info(s"Cancelling Extract text task for FileGroup [$fileGroupId]")
      busyWorkersWithTask(documentSetId).foreach { _ ! CancelTask }
      removeTasksInQueue(documentSetId)
    }
    case DeleteFileUpload(documentSetId, fileGroupId) => {
      Logger.info(s"Deleting upload job for FileGroup [$fileGroupId]")
      taskQueue += DeleteFileUploadJob(documentSetId, fileGroupId)
      jobRequests += (documentSetId -> JobRequest(sender))

      workerPool.map(_ ! TaskAvailable)
    }
    case DeleteFileUploadJobDone(documentSetId, fileGroupId) => {
      busyWorkers -= sender
      jobRequests.get(documentSetId).map { r => 
        r.requester ! FileUploadDeleted(documentSetId, fileGroupId)
        jobRequests -= documentSetId
      }
    }

  }

  private def isNewRequest(documentSetId: Long): Boolean = !jobRequests.contains(documentSetId)

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId)

  private def addNewTasksToQueue(documentSetId: Long, fileGroupId: Long, uploadedFileIds: Set[Long]): Unit = {
    val newTasks = uploadedFileIds.map(CreatePagesTask(documentSetId, fileGroupId, _))
    taskQueue ++= newTasks
    jobTasks += (documentSetId -> uploadedFileIds)
  }

  private def whenTaskIsComplete(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long)(f: (JobRequest, Long, Long, Set[Long]) => Unit) =
    for {
      tasks <- jobTasks.get(documentSetId)
      request <- jobRequests.get(documentSetId)
      remainingTasks = tasks - uploadedFileId
    } f(request, documentSetId, fileGroupId, remainingTasks)

  private def notifyRequesterIfJobIsDone(request: JobRequest, documentSetId: Long, fileGroupId: Long, remainingTasks: Set[Long]): Unit =
    if (remainingTasks.isEmpty) {
      jobTasks -= documentSetId
      jobRequests -= documentSetId

      progressReporter ! CompleteJob(documentSetId)
      request.requester ! FileGroupDocumentsCreated(documentSetId)
    } else {
      jobTasks += (documentSetId -> remainingTasks)
    }

  private def busyWorkersWithTask(documentSetId: Long): Iterable[ActorRef] =
    for {
      (worker, task) <- busyWorkers if task.documentSetId == documentSetId
    } yield worker

  private def removeTasksInQueue(documentSetId: Long): Unit =
    taskQueue.dequeueAll(_.documentSetId == documentSetId)
}

class FileGroupJobQueueImpl(progressReporterActor: ActorRef) extends FileGroupJobQueue {
  class DatabaseStorage extends Storage {
    override def uploadedFileIds(fileGroupId: Long): Set[Long] = Database.inTransaction {
      GroupedFileUploadFinder.byFileGroup(fileGroupId).toIds.toSet
    }
  }

  override protected val storage: Storage = new DatabaseStorage
  override protected val progressReporter: ActorRef = progressReporterActor

}

object FileGroupJobQueue {
  def apply(progressReporter: ActorRef): Props = Props(new FileGroupJobQueueImpl(progressReporter))
}