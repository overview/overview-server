package org.overviewproject.jobhandler.filegroup.task

import akka.actor._
import akka.pattern.pipe
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import scala.concurrent.Await
import org.overviewproject.util.Logger
import FileGroupTaskWorkerFSM._
import akka.actor.Status.Failure
import org.overviewproject.background.filecleanup.FileRemovalRequestQueueProtocol._
import org.overviewproject.database.DocumentSetDeleter
import org.overviewproject.database.FileGroupDeleter
import org.overviewproject.database.DocumentSetCreationJobDeleter
import org.overviewproject.database.TempFileDeleter
import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol.RemoveFileGroup
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.models.tables.GroupedFileUploads
import org.overviewproject.jobhandler.filegroup.task.step.FinalStep
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.models.tables.DocumentProcessingErrors
import org.overviewproject.models.DocumentProcessingError
import org.overviewproject.searchindex.ElasticSearchIndexClient
import org.overviewproject.searchindex.TransportIndexClient
import org.overviewproject.jobhandler.filegroup.task.step.RemoveDeletedObjects
import org.overviewproject.jobhandler.filegroup.task.step.CreateUploadedFileProcess

object FileGroupTaskWorkerProtocol {
  case class RegisterWorker(worker: ActorRef)
  case object TaskAvailable
  case object ReadyForTask
  case object CancelTask

  trait TaskWorkerTask {
    val documentSetId: Long
    val fileGroupId: Long
  }

  case class CreateSearchIndexAlias(documentSetId: Long, fileGroupId: Long) extends TaskWorkerTask
  case class CreateDocuments(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long, options: UploadProcessOptions,
                             documentIdSupplier: ActorRef) extends TaskWorkerTask
  case class CompleteDocumentSet(documentSetId: Long, fileGroupId: Long) extends TaskWorkerTask
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
  case class ExternalActorsFound(jobQueue: Option[ActorRef]) extends Data
  case class ExternalActors(jobQueue: ActorRef) extends Data
  case class TaskInfo(queue: ActorRef, documentSetId: Long, exceptionsHandled: Boolean) extends Data

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
  import context.system
  import context.dispatcher
  import FileGroupTaskWorkerProtocol._

  protected val jobQueueSelection: ActorSelection

  protected val searchIndex: ElasticSearchIndexClient

  private val JobQueueId: String = "Job Queue"

  private val RetryInterval: FiniteDuration = 1 second

  protected def startDeleteFileUploadJob(documentSetId: Long, fileGroupId: Long): TaskStep

  protected def processUploadedFile(documentSetId: Long, uploadedFileId: Long,
                                    options: UploadProcessOptions, documentIdSupplier: ActorRef): TaskStep

  protected def updateDocumentSetInfo(documentSetId: Long): Future[Unit]

  override def preStart = lookForExternalActors

  startWith(LookingForExternalActors, ExternalActorsFound(None))

  when(LookingForExternalActors) {
    case Event(ActorIdentity(JobQueueId, Some(jq)), ExternalActorsFound(_)) => {
      Logger.info(s"[${self.path}] Found Job Queue at ${jq.path}")
      jq ! RegisterWorker(self)

      goto(Ready) using ExternalActors(jq)
    }
    case Event(ActorIdentity(JobQueueId, None), _) => {
      Logger.info(s"[${self.path}] Looking for Job Queue at ${jobQueueSelection.pathString}")
      system.scheduler.scheduleOnce(RetryInterval) { lookForJobQueue }

      stay
    }
  }

  when(Ready) {
    case Event(TaskAvailable, ExternalActors(jobQueue)) => {
      jobQueue ! ReadyForTask
      stay
    }
    case Event(CreateSearchIndexAlias(documentSetId, fileGroupId), ExternalActors(jobQueue)) => {
      searchIndex.addDocumentSet(documentSetId).map { _ => FinalStep } pipeTo self

      goto(Working) using TaskInfo(jobQueue, documentSetId, false)
    }
    case Event(CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier),
      ExternalActors(jobQueue)) => {
      processUploadedFile(documentSetId, uploadedFileId, options, documentIdSupplier).execute pipeTo self

      goto(Working) using TaskInfo(jobQueue, documentSetId, true)
    }
    case Event(CompleteDocumentSet(documentSetId, fileGroupId), ExternalActors(jobQueue)) => {
      updateDocumentSetInfo(documentSetId).map { _ => FinalStep } pipeTo self

      goto(Working) using TaskInfo(jobQueue, documentSetId, false)
    }
    case Event(DeleteFileUploadJob(documentSetId, fileGroupId), ExternalActors(jobQueue)) => {
      startDeleteFileUploadJob(documentSetId, fileGroupId).execute pipeTo self

      goto(Working) using TaskInfo(jobQueue, documentSetId, false)
    }
    case Event(CancelTask, _) => stay
  }

  when(Working) {
    case Event(FinalStep, TaskInfo(jobQueue, documentSetId, _)) => {
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue)
    }
    case Event(step: TaskStep, _) => {
      step.execute pipeTo self
      stay
    }
    case Event(Failure(e), TaskInfo(_, _, false)) => {
      // If exceptions are not handled in the task step, the exception is re-thrown in order to kill
      // the worker and have the job rescheduled.
      Logger.error(e.getMessage)
      throw e
    }
    case Event(Failure(e), TaskInfo(jobQueue, documentSetId, true)) => {
      Logger.error(e.getMessage)

      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue)
    }

    case Event(CancelTask, _)    => goto(Cancelled)
    case Event(TaskAvailable, _) => stay
  }

  when(Cancelled) {
    case Event(step: TaskStep, TaskInfo(jobQueue, documentSetId, _)) => {
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue)
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

  private def lookForExternalActors = {
    lookForJobQueue
  }

}

object FileGroupTaskWorker {
  def apply(jobQueueActorPath: String,
            fileRemovalQueueActorPath: String,
            fileGroupRemovalQueueActorPath: String): Props =
    Props(new FileGroupTaskWorkerImpl(
      jobQueueActorPath,
      fileRemovalQueueActorPath,
      fileGroupRemovalQueueActorPath))

  private class FileGroupTaskWorkerImpl(
    jobQueueActorPath: String,
    fileRemovalQueueActorPath: String,
    fileGroupRemovalQueueActorPath: String) extends FileGroupTaskWorker {

    import context.dispatcher

    override protected val jobQueueSelection = context.actorSelection(jobQueueActorPath)
    
    private val fileRemovalQueue = context.actorSelection(fileRemovalQueueActorPath)
    private val fileGroupRemovalQueue = context.actorSelection(fileGroupRemovalQueueActorPath)

    override protected val searchIndex = TransportIndexClient.singleton

    override protected def startDeleteFileUploadJob(documentSetId: Long, fileGroupId: Long): TaskStep =
      DeleteFileUploadTaskStep(documentSetId, fileGroupId,
        RemoveDeletedObjects(fileGroupId, fileRemovalQueue, fileGroupRemovalQueue))


    protected def processUploadedFile(documentSetId: Long, uploadedFileId: Long,
                                      options: UploadProcessOptions, documentIdSupplier: ActorRef): TaskStep =
      CreateUploadedFileProcess(documentSetId, uploadedFileId, options, documentIdSupplier)

    override protected def updateDocumentSetInfo(documentSetId: Long): Future[Unit] =
      documentSetInfoUpdater.update(documentSetId)

    private val documentSetInfoUpdater = DocumentSetInfoUpdater()
  }
}