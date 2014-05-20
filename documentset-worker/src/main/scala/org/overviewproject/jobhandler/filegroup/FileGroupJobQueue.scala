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
  private val taskQueue: mutable.Queue[CreatePagesTask] = mutable.Queue.empty
  private val jobTasks: mutable.Map[DocumentSetId, Set[Long]] = mutable.Map.empty
  private val jobRequests: mutable.Map[DocumentSetId, JobRequest] = mutable.Map.empty

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
        val task = taskQueue.dequeue
        Logger.info(s"Sending task ${task.uploadedFileId} to ${sender.path.toString}")
        progressReporter ! StartTask(task.documentSetId, task.uploadedFileId)
        sender ! task
      }
    }
    case CreatePagesTaskDone(documentSetId, fileGroupId, uploadedFileId) =>
      Logger.info(s"Task ${uploadedFileId} Done [$fileGroupId]")
      progressReporter ! CompleteTask(documentSetId, uploadedFileId)

      whenTaskIsComplete(documentSetId, fileGroupId, uploadedFileId) {
        notifyRequesterIfJobIsDone
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