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

object FileGroupTaskWorkerProtocol {
  case class RegisterWorker(worker: ActorRef)
  case object TaskAvailable
  case object ReadyForTask
  case object CancelTask

  case class RequestResponse(requestId: Long, documentIds: Seq[Long])

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
  case class ExternalActorsFound(jobQueue: Option[ActorRef], progressReporter: Option[ActorRef]) extends Data
  case class ExternalActors(jobQueue: ActorRef, reporter: ActorRef) extends Data
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
  import context.system
  import context.dispatcher
  import FileGroupTaskWorkerProtocol._

  protected val jobQueueSelection: ActorSelection
  protected val progressReporterSelection: ActorSelection
  protected val fileRemovalQueue: ActorSelection
  protected val fileGroupRemovalQueue: ActorSelection

  protected val uploadedFileProcessCreator: UploadedFileProcessCreator
  protected val searchIndex: ElasticSearchIndexClient

  private val NumberOfExternalActors = 2
  private val JobQueueId: String = "Job Queue"
  private val ProgressReporterId: String = "Progress Reporter"

  private val RetryInterval: FiniteDuration = 1 second

  private case class DeleteFileUploadJobComplete(documentSetId: Long)

  protected def startDeleteFileUploadJob(documentSetId: Long, fileGroupId: Long): FileGroupTaskStep

  protected def findUploadedFile(uploadedFileId: Long): Future[Option[GroupedFileUpload]]
  protected def writeDocumentProcessingError(documentSetId: Long, filename: String, message: String): Future[Unit]
  protected def updateDocumentSetInfo(documentSetId: Long): Future[Unit]

  override def preStart = lookForExternalActors

  startWith(LookingForExternalActors, ExternalActorsFound(None, None))

  when(LookingForExternalActors) {
    case Event(ActorIdentity(JobQueueId, Some(jq)), ExternalActorsFound(_, pr)) => {
      Logger.info(s"[${self.path}] Found Job Queue at ${jq.path}")
      jq ! RegisterWorker(self)

      pr.fold(stay using ExternalActorsFound(Some(jq), pr))(goto(Ready) using ExternalActors(jq, _))
    }
    case Event(ActorIdentity(JobQueueId, None), _) => {
      Logger.info(s"[${self.path}] Looking for Job Queue at ${jobQueueSelection.pathString}")
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
    case Event(CreateSearchIndexAlias(documentSetId, fileGroupId), ExternalActors(jobQueue, progressReporter)) => {
      searchIndex.addDocumentSet(documentSetId).map { _ => FinalStep } pipeTo self

      goto(Working) using TaskInfo(jobQueue, progressReporter, documentSetId, fileGroupId, 0)
    }
    case Event(CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier),
      ExternalActors(jobQueue, progressReporter)) => {
      findUploadedFile(uploadedFileId).flatMap { uploadedFile =>
        val process = uploadedFileProcessCreator.create(uploadedFile.get, options, documentSetId, documentIdSupplier)
        process.start(uploadedFile.get)
      } pipeTo self

      goto(Working) using TaskInfo(jobQueue, progressReporter, documentSetId, fileGroupId, uploadedFileId)
    }
    case Event(CompleteDocumentSet(documentSetId, fileGroupId), ExternalActors(jobQueue, progressReporter)) => {
      updateDocumentSetInfo(documentSetId).map { _ => FinalStep } pipeTo self

      goto(Working) using TaskInfo(jobQueue, progressReporter, documentSetId, fileGroupId, 0)
    }
    case Event(DeleteFileUploadJob(documentSetId, fileGroupId), ExternalActors(jobQueue, progressReporter)) => {
      executeTaskStep(startDeleteFileUploadJob(documentSetId, fileGroupId))

      goto(Working) using TaskInfo(jobQueue, progressReporter, documentSetId, fileGroupId, 0)
    }
    case Event(CancelTask, _) => stay
  }

  when(Working) {
    case Event(DeleteFileUploadComplete(documentSetId, fileGroupId), TaskInfo(jobQueue, progressReporter, _, _, _)) => {
      fileRemovalQueue ! RemoveFiles
      fileGroupRemovalQueue ! RemoveFileGroup(fileGroupId)
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue, progressReporter)
    }
    case Event(FinalStep, TaskInfo(jobQueue, progressReporter, documentSetId, _, _)) => {
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue, progressReporter)
    }
    case Event(step: TaskStep, _) => {
      step.execute pipeTo self
      stay
    }
    case Event(Failure(e), TaskInfo(_, _, _, _, 0)) => {
      // uploadedFileId == 0 if the task is something other than creating a document from an upload
      // We don't handle errors in this situation so the exception is rethrown in order to kill
      // the worker and have the job rescheduled.
      Logger.error(e.getMessage)
      throw e
    }
    case Event(Failure(e), TaskInfo(jobQueue, progressReporter, documentSetId, _, uploadedFileId)) => {
      Logger.error(e.getMessage)
      // FIXME: don't load info that should already be available
      for {
        upload <- findUploadedFile(uploadedFileId)
        r <- writeDocumentProcessingError(documentSetId, upload.get.name, e.getMessage)
      } {
        jobQueue ! TaskDone(documentSetId, None)
        jobQueue ! ReadyForTask
      }

      goto(Ready) using ExternalActors(jobQueue, progressReporter)
    }

    case Event(step: FileGroupTaskStep, _) => {
      executeTaskStep(step)

      stay
    }
    case Event(CancelTask, _)    => goto(Cancelled)
    case Event(TaskAvailable, _) => stay
  }

  when(Cancelled) {
    case Event(step: TaskStep, TaskInfo(jobQueue, progressReporter, documentSetId, fileGroupId, uploadedFileId)) => {
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue, progressReporter)
    }

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

}

object FileGroupTaskWorker {
  def apply(jobQueueActorPath: String,
            progressReporterActorPath: String,
            fileRemovalQueueActorPath: String,
            fileGroupRemovalQueueActorPath: String): Props =
    Props(new FileGroupTaskWorkerImpl(
      jobQueueActorPath,
      progressReporterActorPath,
      fileRemovalQueueActorPath,
      fileGroupRemovalQueueActorPath))

  private class FileGroupTaskWorkerImpl(
    jobQueueActorPath: String,
    progressReporterActorPath: String,
    fileRemovalQueueActorPath: String,
    fileGroupRemovalQueueActorPath: String) extends FileGroupTaskWorker with SlickSessionProvider {

    import org.overviewproject.database.Slick.simple._
    import context.dispatcher

    override protected val jobQueueSelection = context.actorSelection(jobQueueActorPath)
    override protected val progressReporterSelection = context.actorSelection(progressReporterActorPath)
    override protected val fileRemovalQueue = context.actorSelection(fileRemovalQueueActorPath)
    override protected val fileGroupRemovalQueue = context.actorSelection(fileGroupRemovalQueueActorPath)

    override protected val uploadedFileProcessCreator = UploadedFileProcessCreator()
    override protected val searchIndex = TransportIndexClient.singleton

    override protected def startDeleteFileUploadJob(documentSetId: Long, fileGroupId: Long): FileGroupTaskStep =
      new DeleteFileUploadTaskStep(documentSetId, fileGroupId,
        DocumentSetCreationJobDeleter(), DocumentSetDeleter(), FileGroupDeleter(), TempFileDeleter())

    override protected def findUploadedFile(uploadedFileId: Long): Future[Option[GroupedFileUpload]] = db { implicit session =>
      GroupedFileUploads.filter(_.id === uploadedFileId).firstOption
    }

    override protected def writeDocumentProcessingError(documentSetId: Long, filename: String,
                                                        message: String): Future[Unit] = db { implicit session =>
      DocumentProcessingErrors
        .map(dpe => (dpe.documentSetId, dpe.textUrl, dpe.message))
        .insert((documentSetId, filename, message))

    }

    override protected def updateDocumentSetInfo(documentSetId: Long): Future[Unit] =
      documentSetInfoUpdater.update(documentSetId)

    private val documentSetInfoUpdater = DocumentSetInfoUpdater()
  }
}