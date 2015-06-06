package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated

import org.overviewproject.util.Logger
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import org.overviewproject.jobhandler.filegroup.task.UploadProcessOptions

trait FileGroupJob {
  val fileGroupId: Long
}

case class CreateDocumentsJob(fileGroupId: Long, options: UploadProcessOptions) extends FileGroupJob
case class DeleteFileGroupJob(fileGroupId: Long) extends FileGroupJob

object FileGroupJobQueueProtocol {
  case class SubmitJob(documentSetId: Long, job: FileGroupJob)
  case class JobCompleted(documentSetId: Long)
  case class AddTasks(tasks: Iterable[TaskWorkerTask])

  case class CancelFileUpload(documentSetId: Long, fileGroupId: Long)
}

/**
 * FileGroupJobQueue manages FileGroup related jobs.
 *  - `CreateDocumentsJobs` are split into tasks for each uploaded file in the file group. Each task results
 *  the uploaded file being converted into a file and pages, which are later used to create documents.
 *  - `DeleteFileGroupJobs` are one task that delete all entries in the database related to a cancelled file upload job
 *  - CancelFileUpload results in a CancelTask message sent to workers processing uploads in the specified file group.
 *  The JobQueue doesn't distinguish between cancelled and completed task (because a successful task completion message
 *  can be received after the cancellation message has been received). The JobQueue expects only one response from any
 *  worker working on a task. Once all tasks have been completed or cancelled, the requester is notified that the job
 *  is complete.
 *  A CanceFilelUpload message may be received for an unknown job during restart and recovery from an unexpected shutdown. In
 *  this case, the JobQueue responds as if the job has been successfully cancelled.
 *  @todo Rename CancelFileUpload to be more generic and make it sure it works for any jobs.
 *
 *
 *  The FileGroupJobQueue waits for workers to register. As tasks become available, workers are notified. Idle workers
 *  respond, and are handed tasks. Workers are notified when they register, and when new tasks are added to the task queue.
 *  Workers should signal that they are ready to accept tasks when they are idle (after completing a task).
 *
 */
trait FileGroupJobQueue extends Actor {
  import FileGroupJobQueueProtocol._

  type DocumentSetId = Long

  protected val progressReporter: ActorRef
  protected val documentIdSupplier: ActorRef
  protected val jobShepherdFactory: JobShepherdFactory

  private case class JobRequest(requester: ActorRef)

  private val workerPool: mutable.Set[ActorRef] = mutable.Set.empty
  private val taskQueue: mutable.Queue[TaskWorkerTask] = mutable.Queue.empty
  private val jobShepherds: mutable.Map[DocumentSetId, JobShepherd] = mutable.Map.empty
  private val jobRequests: mutable.Map[DocumentSetId, JobRequest] = mutable.Map.empty
  private val busyWorkers: mutable.Map[ActorRef, TaskWorkerTask] = mutable.Map.empty

  def receive = {
    case RegisterWorker(worker) => {
      Logger.info(s"Registering worker ${worker.path.toString}")
      context.watch(worker)
      workerPool += worker
      if (!taskQueue.isEmpty) worker ! TaskAvailable

    }

    case SubmitJob(documentSetId, job) =>
      if (isNewRequest(documentSetId)) {
        val shepherd = jobShepherdFactory.createShepherd(documentSetId, job, self, progressReporter, documentIdSupplier)
        val numberOfTasks = shepherd.createTasks

        jobShepherds += (documentSetId -> shepherd)

        jobRequests += (documentSetId -> JobRequest(sender))
      }

    case ReadyForTask => {
      if (workerIsFree(sender) && !taskQueue.isEmpty) {
        val task = taskQueue.dequeue
        Logger.info(s"(${task.documentSetId}:${task.fileGroupId}) Sending task $task to ${sender.path.toString}")
        jobShepherds.get(task.documentSetId).map(_.startTask(task))

        sender ! task
        busyWorkers += (sender -> task)
      }
    }

    case TaskDone(documentSetId, outputFileId) => {
      val task = busyWorkers.remove(sender)
      Logger.info(s"($documentSetId) Task ${task.getOrElse("Not Found")}  Done")

      whenTaskIsComplete(documentSetId, task) {
        notifyRequesterIfJobIsDone
      }
    }

    case CancelFileUpload(documentSetId, fileGroupId) => {
      Logger.info(s"($documentSetId:$fileGroupId) Cancelling Extract text tasks")
      jobRequests.get(documentSetId).fold {
        sender ! JobCompleted(documentSetId)
      } { r =>
        busyWorkersWithTask(documentSetId).foreach { _ ! CancelTask }
        for (shepherd <- jobShepherds.get(documentSetId)) {
          removeTasksInQueue(documentSetId)
          shepherd.removeNotStartedTasks
          notifyRequesterIfJobIsDone(r, documentSetId, shepherd)
        }
      }
    }

    case AddTasks(tasks) => {
      taskQueue ++= tasks

      notifyWorkers
    }

    case Terminated(worker) => {
      Logger.info(s"Removing ${worker.path.toString} from worker pool")
      workerPool -= worker
      busyWorkers.get(worker).map { task =>
        busyWorkers -= worker
        taskQueue += task
      }

      notifyWorkers
    }

  }

  private def notifyWorkers: Unit = workerPool.filter(workerIsFree).map { _ ! TaskAvailable }

  private def workerIsFree(worker: ActorRef): Boolean = busyWorkers.get(worker).isEmpty

  private def isNewRequest(documentSetId: Long): Boolean = !jobRequests.contains(documentSetId)

  private def whenTaskIsComplete(documentSetId: Long, task: Option[TaskWorkerTask])(f: (JobRequest, Long, JobShepherd) => Unit) =
    for {
      completedTask <- task
      request <- jobRequests.get(documentSetId)
      shepherd <- jobShepherds.get(documentSetId)
    } {
      shepherd.completeTask(completedTask)
      f(request, documentSetId, shepherd)
    }

  private def notifyRequesterIfJobIsDone(request: JobRequest, documentSetId: Long, shepherd: JobShepherd): Unit =
    if (shepherd.allTasksComplete) {
      jobRequests -= documentSetId

      progressReporter ! CompleteJob(documentSetId)
      request.requester ! JobCompleted(documentSetId)
    }

  private def busyWorkersWithTask(documentSetId: Long): Iterable[ActorRef] =
    for {
      (worker, task) <- busyWorkers if task.documentSetId == documentSetId
    } yield worker

  private def removeTasksInQueue(documentSetId: Long): Unit =
    taskQueue.dequeueAll(_.documentSetId == documentSetId)

}

object FileGroupJobQueue {
  def apply(progressReporter: ActorRef, documentIdSupplier: ActorRef): Props =
    Props(new FileGroupJobQueueImpl(progressReporter, documentIdSupplier))

  private class FileGroupJobQueueImpl(override protected val progressReporter: ActorRef,
                                      override protected val documentIdSupplier: ActorRef) extends FileGroupJobQueue {

    override protected val jobShepherdFactory = new FileGroupJobShepherdFactory

  }

}
