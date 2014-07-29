package org.overviewproject.jobhandler.filegroup.task

import akka.actor._
import akka.pattern.pipe
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import org.overviewproject.util.Logger
import FileGroupTaskWorkerFSM._
import scala.util.control.Exception._

object FileGroupTaskWorkerProtocol {
  case class RegisterWorker(worker: ActorRef)
  case object TaskAvailable
  case object ReadyForTask
  case object CancelTask
  
  trait TaskWorkerTask {
    val documentSetId: Long
    val fileGroupId: Long
  }
  
  case class CreatePagesTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long) extends TaskWorkerTask
  case class DeleteFileUploadJob(documentSetId: Long, fileGroupId: Long)  extends TaskWorkerTask

  case class TaskDone(documentSetId: Long, outputId: Option[Long])
}


object FileGroupTaskWorkerFSM {
  sealed trait State
  case object LookingForJobQueue extends State
  case object Ready extends State
  case object Working extends State
  case object Cancelled extends State

  sealed trait Data
  case object NoKnownJobQueue extends Data
  case class JobQueue(queue: ActorRef) extends Data
  case class TaskInfo(queue: ActorRef, documentSetId: Long, fileGroupId: Long, uploadedFileId: Long) extends Data

}

/**
 * A worker that registers with the [[FileGroupJobQueue]] and can handle [[CreatePagesTask]]s 
 * and [[DeleteFileUploadTask]]s.
 * The worker handles [[Exception]]s during file processing by creating [[DocumentProcessingError]]s for the
 * [[File]] being processed. If an [[Exception]] is thrown during a [[DeleteFileUploadJob]], it's logged and ignored.
 * 
 * @todo Move to separate JVM and instance
 * @todo Add Death Watch on [[FileGroupJobQueue]]. If the queue dies, the task should be abandoned, and 
 *   the worker should wait for its rebirth.
 */
trait FileGroupTaskWorker extends Actor with FSM[State, Data] {
  import context._
  import FileGroupTaskWorkerProtocol._
  
  protected def jobQueuePath: String

  private val JobQueueId: String = "Job Queue"
  private val RetryInterval: FiniteDuration = 1 second

  private val jobQueueSelection = system.actorSelection(jobQueuePath)
  private var jobQueue: ActorRef = _

  protected def startCreatePagesTask(documentSetId: Long, uploadedFileId: Long): FileGroupTaskStep
  protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Unit
  
  lookForJobQueue

  startWith(LookingForJobQueue, NoKnownJobQueue)

  when(LookingForJobQueue) {
    case Event(ActorIdentity(JobQueueId, Some(jq)), _) => {
      Logger.info(s"[${self.path}] Found Job Queue at ${jq.path}")
      jq ! RegisterWorker(self)

      goto(Ready) using JobQueue(jq)
    }
    case Event(ActorIdentity(JobQueueId, None), _) => {
      Logger.info(s"[${self.path}] Looking for Job Queue at $jobQueuePath")
      system.scheduler.scheduleOnce(RetryInterval) { lookForJobQueue }

      stay
    }
  }

  when(Ready) {
    case Event(TaskAvailable, JobQueue(jobQueue)) => {
      jobQueue ! ReadyForTask
      stay
    }
    case Event(CreatePagesTask(documentSetId, fileGroupId, uploadedFileId), JobQueue(jobQueue)) => {
      executeTaskStep(startCreatePagesTask(documentSetId, uploadedFileId))
      goto(Working) using TaskInfo(jobQueue, documentSetId, fileGroupId, uploadedFileId)
    }
    case Event(DeleteFileUploadJob(documentSetId, fileGroupId), JobQueue(jobQueue)) => {
      ignoringExceptions { deleteFileUploadJob(documentSetId, fileGroupId) }
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask
      
      stay
    }
    case Event(CancelTask, _) => stay
  }

  when(Working) {
    case Event(CreatePagesProcessComplete(documentSetId, uploadedFileId, fileId), TaskInfo(jobQueue, _, _, _)) => {
      jobQueue ! TaskDone(documentSetId, fileId)
      jobQueue ! ReadyForTask

      goto(Ready) using JobQueue(jobQueue)
    }
    case Event(step: FileGroupTaskStep, _) => {
      executeTaskStep(step)

      stay
    }
    case Event(CancelTask, _) => goto(Cancelled)
    case Event(TaskAvailable, _) => stay    
  }

  when(Cancelled) {
    case Event(step: FileGroupTaskStep, TaskInfo(jobQueue, documentSetId, fileGroupId, uploadedFileId)) => {
      step.cancel
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using JobQueue(jobQueue)
    }
    case Event(TaskAvailable, _) => stay        
  }

  private def lookForJobQueue = jobQueueSelection ! Identify(JobQueueId)

  private def executeTaskStep(step: FileGroupTaskStep) = Future { step.execute } pipeTo self
  
  private def ignoringExceptions = handling(classOf[Exception]) by {e => Logger.error(e.toString) }
}

object FileGroupTaskWorker {
  def apply(fileGroupJobQueuePath: String): Props = Props(new FileGroupTaskWorker with CreatePagesFromPdfWithStorage {
    override protected def jobQueuePath: String = fileGroupJobQueuePath
    override protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Unit = 
      FileUploadDeleter().deleteFileUpload(documentSetId, fileGroupId)
  })
}