package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import akka.actor.Actor
import akka.actor.ActorRef
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import akka.actor.Props
import org.overviewproject.util.Logger

object FileGroupJobQueueProtocol {
  case class CreateDocumentsFromFileGroup(fileGroupId: Long)
  case class FileGroupDocumentsCreated(documentSetId: Long)
}

object FileGroupTaskWorkerProtocol {
  case class RegisterWorker(worker: ActorRef)
  case object TaskAvailable
  case object ReadyForTask
  case class CreatePagesTask(fileGroupId: Long, uploadedFileId: Long)
  case class CreatePagesTaskDone(fileGroupId: Long, uploadedFileId: Long)
}

trait FileGroupJobQueue extends Actor {
  import FileGroupJobQueueProtocol._
  import FileGroupTaskWorkerProtocol._

  protected val storage: Storage

  trait Storage {
    def uploadedFileIds(fileGroupId: Long): Set[Long]
  }

  private case class AddTasks(tasks: Iterable[CreatePagesTask])
  private case class JobRequest(requester: ActorRef)

  private val workerPool: mutable.Set[ActorRef] = mutable.Set.empty
  private val taskQueue: mutable.Queue[CreatePagesTask] = mutable.Queue.empty
  private val jobTasks: mutable.Map[Long, Set[Long]] = mutable.Map.empty
  private val jobRequests: mutable.Map[Long, JobRequest] = mutable.Map.empty

  def receive = {
    case RegisterWorker(worker) => {
      Logger.info(s"Registering worker ${worker.path.toString}")
      workerPool += worker
      if (!taskQueue.isEmpty) worker ! TaskAvailable

    }
    case CreateDocumentsFromFileGroup(fileGroupId) => {
      Logger.info(s"Extact text task for FileGroup [$fileGroupId]")
      val fileIds = uploadedFilesInFileGroup(fileGroupId)

      addNewTasksToQueue(fileGroupId, fileIds)
      jobRequests += (fileGroupId -> JobRequest(sender))

      workerPool.map(_ ! TaskAvailable)
    }
    case ReadyForTask => {
      if (!taskQueue.isEmpty) {
        val task = taskQueue.dequeue
        Logger.info(s"Sending task ${task.uploadedFileId} to ${sender.path.toString}")
        sender ! task
      }
    }
    case CreatePagesTaskDone(fileGroupId: Long, uploadedFileId: Long) =>
      Logger.info(s"Task ${uploadedFileId} Done [$fileGroupId]")
      whenTaskIsComplete(fileGroupId, uploadedFileId) {
        notifyRequesterIfJobIsDone
      }

  }

  private def uploadedFilesInFileGroup(fileGroupId: Long): Set[Long] = storage.uploadedFileIds(fileGroupId)

  private def addNewTasksToQueue(fileGroupId: Long, uploadedFileIds: Set[Long]): Unit = {
    val newTasks = uploadedFileIds.map(CreatePagesTask(fileGroupId, _))
    taskQueue ++= newTasks
    jobTasks += (fileGroupId -> uploadedFileIds)
  }

  private def whenTaskIsComplete(fileGroupId: Long, uploadedFileId: Long)(f: (JobRequest, Long, Set[Long]) => Unit) =
    for {
      tasks <- jobTasks.get(fileGroupId)
      request <- jobRequests.get(fileGroupId)
      remainingTasks = tasks - uploadedFileId
    } f(request, fileGroupId, remainingTasks)

  private def notifyRequesterIfJobIsDone(request: JobRequest, fileGroupId: Long, remainingTasks: Set[Long]): Unit =
    if (remainingTasks.isEmpty) {
      jobTasks -= fileGroupId
      jobRequests -= fileGroupId

      request.requester ! FileGroupDocumentsCreated(fileGroupId)
    } else {
      jobTasks += (fileGroupId -> remainingTasks)
    }
}

class FileGroupJobQueueImpl extends FileGroupJobQueue {
  class DatabaseStorage extends Storage {
    override def uploadedFileIds(fileGroupId: Long): Set[Long] = Database.inTransaction {
      GroupedFileUploadFinder.byFileGroup(fileGroupId).toIds.toSet
    }
  }

  override protected val storage: Storage = new DatabaseStorage

}

object FileGroupJobQueue {
  def apply(): Props = Props[FileGroupJobQueueImpl]
}