package org.overviewproject.jobhandler.filegroup.task

import akka.actor._
import akka.pattern.pipe
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import org.overviewproject.util.Logger
import FileGroupTaskWorkerFSM._
import scala.util.control.Exception._
import akka.actor.Status.Failure

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
  case class CreateDocumentsTask(documentSetId: Long, fileGroupId: Long, splitDocuments: Boolean) extends TaskWorkerTask
  case class DeleteFileUploadJob(documentSetId: Long, fileGroupId: Long) extends TaskWorkerTask

  case class TaskDone(documentSetId: Long, outputId: Option[Long])
}

object FileGroupTaskWorkerFSM {
  sealed trait State
  case object LookingForExternalActors extends State
  case object Ready extends State
  case object Working extends State
  case object Cancelled extends State

  sealed trait Data
  case class ExternalActorsFound(jobQueue: Option[ActorRef], progressReporter: Option[ActorRef]) extends Data
  case class ExternalActors(queue: ActorRef, reporter: ActorRef) extends Data
  case class TaskInfo(queue: ActorRef, reporter: ActorRef, documentSetId: Long, fileGroupId: Long, uploadedFileId: Long) extends Data

}

/**
 * A worker that registers with the [[FileGroupJobQueue]] and can handle [[CreatePagesTask]]s
 * and [[DeleteFileUploadJob]]s.
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
  protected def progressReporterPath: String

  private val NumberOfExternalActors = 2
  private val JobQueueId: String = "Job Queue"
  private val ProgressReporterId: String = "Progress Reporter"

  private val RetryInterval: FiniteDuration = 1 second

  private val jobQueueSelection = system.actorSelection(jobQueuePath)

  private val progressReporterSelection = system.actorSelection(progressReporterPath)

  protected def startCreatePagesTask(documentSetId: Long, uploadedFileId: Long): FileGroupTaskStep
  protected def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean,
                                         progressReporter: ActorRef): FileGroupTaskStep

  protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Unit

  lookForExternalActors

  startWith(LookingForExternalActors, ExternalActorsFound(None, None))

  when(LookingForExternalActors) {
    case Event(ActorIdentity(JobQueueId, Some(jq)), ExternalActorsFound(_, pr)) => {
      Logger.info(s"[${self.path}] Found Job Queue at ${jq.path}")
      jq ! RegisterWorker(self)

      pr.fold(stay using ExternalActorsFound(Some(jq), pr))(goto(Ready) using ExternalActors(jq, _))
    }
    case Event(ActorIdentity(JobQueueId, None), _) => {
      Logger.info(s"[${self.path}] Looking for Job Queue at $jobQueuePath")
      system.scheduler.scheduleOnce(RetryInterval) { lookForJobQueue }

      stay
    }
    case Event(ActorIdentity(ProgressReporterId, Some(pr)), ExternalActorsFound(jq, _)) => {
      Logger.info(s"[${self.path}] Found Progress Reporter at ${pr.path}")

      jq.fold(stay using ExternalActorsFound(None, Some(pr)))(goto(Ready) using ExternalActors(_, pr))
    }
  }

  when(Ready) {
    case Event(TaskAvailable, ExternalActors(jobQueue, _)) => {
      jobQueue ! ReadyForTask
      stay
    }
    case Event(CreatePagesTask(documentSetId, fileGroupId, uploadedFileId), ExternalActors(jobQueue, progressReporter)) => {
      executeTaskStep(startCreatePagesTask(documentSetId, uploadedFileId))
      goto(Working) using TaskInfo(jobQueue, progressReporter, documentSetId, fileGroupId, uploadedFileId)
    }
    case Event(CreateDocumentsTask(documentSetId, fileGroupId, splitDocuments), ExternalActors(jobQueue, progressReporter)) => {
      executeTaskStep(startCreateDocumentsTask(documentSetId, splitDocuments, progressReporter))
      goto(Working) using TaskInfo(jobQueue, progressReporter, documentSetId, fileGroupId, 0)
    }
    case Event(DeleteFileUploadJob(documentSetId, fileGroupId), ExternalActors(jobQueue, progressReporter)) => {
      ignoringExceptions { deleteFileUploadJob(documentSetId, fileGroupId) }
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      stay
    }
    case Event(CancelTask, _) => stay
  }

  when(Working) {
    case Event(CreatePagesProcessComplete(documentSetId, uploadedFileId, fileId), TaskInfo(jobQueue, progressReporter, _, _, _)) => {
      jobQueue ! TaskDone(documentSetId, fileId)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue, progressReporter)
    }
    case Event(CreateDocumentsProcessComplete(documentSetId), TaskInfo(jobQueue,progressReporter, _, _, _)) => {
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue, progressReporter)
    }
    case Event(step: FileGroupTaskStep, _) => {
      executeTaskStep(step)

      stay
    }
    case Event(CancelTask, _) => goto(Cancelled)
    case Event(TaskAvailable, _) => stay
  }

  when(Cancelled) {
    case Event(step: FileGroupTaskStep, TaskInfo(jobQueue, progressReporter, documentSetId, fileGroupId, uploadedFileId)) => {
      step.cancel
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue, progressReporter)
    }
    case Event(TaskAvailable, _) => stay
  }

  whenUnhandled {
    case Event(Failure(e), _) => throw e // Escalate unhandled exceptions
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  private def lookForJobQueue = jobQueueSelection ! Identify(JobQueueId)
  private def lookForProgressReporter = progressReporterSelection ! Identify(ProgressReporterId)

  private def lookForExternalActors = {
    lookForJobQueue
    lookForProgressReporter
  }

  private def executeTaskStep(step: FileGroupTaskStep) = Future { step.execute } pipeTo self

  private def ignoringExceptions = handling(classOf[Exception]) by { e => Logger.error(e.toString) }
}

object FileGroupTaskWorker {
  def apply(jobQueueActorPath: String, progressReporterActorPath: String): Props = Props(new FileGroupTaskWorker with CreatePagesFromPdfWithStorage with CreateDocumentsWithStorage {
    override protected def jobQueuePath: String = jobQueueActorPath
    override protected def progressReporterPath: String = progressReporterActorPath

    override protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Unit =
      FileUploadDeleter().deleteFileUpload(documentSetId, fileGroupId)
  })
}