package com.overviewdocs.jobhandler.filegroup

import scala.collection.mutable
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated

import com.overviewdocs.jobhandler.filegroup.ProgressReporterProtocol._
import com.overviewdocs.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import com.overviewdocs.jobhandler.filegroup.task.UploadProcessOptions
import com.overviewdocs.util.Logger

case class CreateDocumentsJob(fileGroupId: Long, options: UploadProcessOptions)

object FileGroupJobQueueProtocol {
  case class SubmitJob(documentSetId: Long, job: CreateDocumentsJob)
  case class JobCompleted(documentSetId: Long)
  case class AddTasks(tasks: Iterable[TaskWorkerTask])
}

/**
 * FileGroupJobQueue manages FileGroup related jobs.
 *  - `CreateDocumentsJobs` are split into tasks for each uploaded file in the file group. Each task results
 *  the uploaded file being converted into a file and pages, which are later used to create documents.
 *  The JobQueue doesn't distinguish between cancelled and completed task (because a successful task completion message
 *  can be received after the cancellation message has been received). The JobQueue expects only one response from any
 *  worker working on a task. Once all tasks have been completed or cancelled, the requester is notified that the job
 *  is complete.
 *  this case, the JobQueue responds as if the job has been successfully cancelled.
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

  protected val logger = Logger.forClass(getClass)

  protected val progressReporter: ActorRef
  protected val documentIdSupplier: ActorRef
  protected val jobShepherdFactory: JobShepherdFactory

  private case class JobRequest(requester: ActorRef)

  private val workerPool: mutable.Set[ActorRef] = mutable.Set.empty
  private val taskQueue: TaskQueue = new RoundRobinTaskQueue
  private val jobShepherds: mutable.Map[DocumentSetId, CreateDocumentsJobShepherd] = mutable.Map.empty
  private val jobRequests: mutable.Map[DocumentSetId, JobRequest] = mutable.Map.empty
  private val busyWorkers: mutable.Map[ActorRef, TaskWorkerTask] = mutable.Map.empty

  def receive = {
    case RegisterWorker(worker) => {
      logger.info("Registering worker {}", worker.path.toString)
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
        logger.info("Sending task(documentSetId={},fileGroupId={},task={}) to {}", task.documentSetId, task.fileGroupId, task.toString, sender.path.toString)
        jobShepherds.get(task.documentSetId).map(_.startTask(task))

        sender ! task
        busyWorkers += (sender -> task)
      }
    }

    case TaskDone(documentSetId, outputFileId) => {
      val task = busyWorkers.remove(sender)
      logger.info("Task (documentSetId={},task={}) done", documentSetId, task.map(_.toString).getOrElse("[not found]"))

      whenTaskIsComplete(documentSetId, task) {
        notifyRequesterIfJobIsDone
      }
    }

    case AddTasks(tasks) => {
      taskQueue.addTasks(tasks)

      notifyWorkers
    }

    case Terminated(worker) => {
      logger.info("Removing worker {} from worker pool", worker.path.toString)
      workerPool -= worker
      busyWorkers.get(worker).map { task =>
        busyWorkers -= worker
        taskQueue.addTask(task)
      }

      notifyWorkers
    }

  }

  private def notifyWorkers: Unit = workerPool.filter(workerIsFree).map { _ ! TaskAvailable }

  private def workerIsFree(worker: ActorRef): Boolean = busyWorkers.get(worker).isEmpty

  private def isNewRequest(documentSetId: Long): Boolean = !jobRequests.contains(documentSetId)

  private def whenTaskIsComplete(documentSetId: Long, task: Option[TaskWorkerTask])(f: (JobRequest, Long, CreateDocumentsJobShepherd) => Unit) =
    for {
      completedTask <- task
      request <- jobRequests.get(documentSetId)
      shepherd <- jobShepherds.get(documentSetId)
    } {
      shepherd.completeTask(completedTask)
      f(request, documentSetId, shepherd)
    }

  private def notifyRequesterIfJobIsDone(request: JobRequest, documentSetId: Long, shepherd: CreateDocumentsJobShepherd): Unit =
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
    taskQueue.removeAll(_.documentSetId == documentSetId)

}

object FileGroupJobQueue {
  def apply(progressReporter: ActorRef, documentIdSupplier: ActorRef): Props =
    Props(new FileGroupJobQueueImpl(progressReporter, documentIdSupplier))

  private class FileGroupJobQueueImpl(override protected val progressReporter: ActorRef,
                                      override protected val documentIdSupplier: ActorRef) extends FileGroupJobQueue {

    override protected val jobShepherdFactory = new FileGroupJobShepherdFactory

  }

}
