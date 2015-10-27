package com.overviewdocs.jobhandler.filegroup.task

import akka.actor._
import akka.pattern.pipe
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure,Success,Try}

import com.overviewdocs.background.filecleanup.FileRemovalRequestQueueProtocol
import com.overviewdocs.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.database.DocumentSetDeleter
import com.overviewdocs.database.FileGroupDeleter
import com.overviewdocs.database.DocumentSetCreationJobDeleter
import com.overviewdocs.database.TempFileDeleter
import com.overviewdocs.models.tables.GroupedFileUploads
import com.overviewdocs.searchindex.ElasticSearchIndexClient
import com.overviewdocs.searchindex.TransportIndexClient
import com.overviewdocs.util.Logger
import com.overviewdocs.util.BulkDocumentWriter

import FileGroupTaskWorkerFSM._

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

  sealed trait Data
  case class ExternalActorsFound(jobQueue: Option[ActorRef]) extends Data
  case class ExternalActors(jobQueue: ActorRef) extends Data
  case class TaskInfo(queue: ActorRef, documentSetId: Long) extends Data

}

/**
 * A worker that registers with the [[FileGroupJobQueue]] and can handle [[CreatePagesTask]]s
 * and [[DeleteFileUploadJob]]s.
 * The worker handles [[Exception]]s during file processing by creating [[DocumentProcessingError]]s for the
 * [[File]] being processed.
 *
 * @todo Move to separate JVM and instance
 * @todo Add Death Watch on [[FileGroupJobQueue]]. If the queue dies, the task should be abandoned, and
 *   the worker should wait for its rebirth.
 */
trait FileGroupTaskWorker extends Actor with FSM[State, Data] {
  import context.system
  import context.dispatcher
  import FileGroupTaskWorkerProtocol._

  protected val logger = Logger.forClass(getClass)

  protected val jobQueueSelection: ActorSelection

  protected val searchIndex: ElasticSearchIndexClient

  private val JobQueueId: String = "Job Queue"

  private val RetryInterval: FiniteDuration = 1 second

  protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Future[Unit]

  protected def processUploadedFile(documentSetId: Long, uploadedFileId: Long,
                                    options: UploadProcessOptions, documentIdSupplier: ActorRef): Future[Unit]

  protected def updateDocumentSetInfo(documentSetId: Long): Future[Unit]

  override def preStart = lookForExternalActors

  startWith(LookingForExternalActors, ExternalActorsFound(None))

  when(LookingForExternalActors) {
    case Event(ActorIdentity(JobQueueId, Some(jq)), ExternalActorsFound(_)) => {
      logger.info("[{}] Found job queue, path={}", self.path, jq.path)
      jq ! RegisterWorker(self)

      goto(Ready) using ExternalActors(jq)
    }
    case Event(ActorIdentity(JobQueueId, None), _) => {
      logger.info("[{}] Looking for job queue at {}", self.path, jobQueueSelection.pathString)
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
      searchIndex.addDocumentSet(documentSetId)
        .andThen { case x => self ! x }

      goto(Working) using TaskInfo(jobQueue, documentSetId)
    }
    case Event(CreateDocuments(documentSetId, fileGroupId, uploadedFileId, options, documentIdSupplier), ExternalActors(jobQueue)) => {
      processUploadedFile(documentSetId, uploadedFileId, options, documentIdSupplier)
        .andThen { case x => self ! x }

      goto(Working) using TaskInfo(jobQueue, documentSetId)
    }
    case Event(CompleteDocumentSet(documentSetId, fileGroupId), ExternalActors(jobQueue)) => {
      updateDocumentSetInfo(documentSetId)
        .andThen { case x => self ! x }

      goto(Working) using TaskInfo(jobQueue, documentSetId)
    }
    case Event(DeleteFileUploadJob(documentSetId, fileGroupId), ExternalActors(jobQueue)) => {
      deleteFileUploadJob(documentSetId, fileGroupId)
        .andThen { case x => self ! x }

      goto(Working) using TaskInfo(jobQueue, documentSetId)
    }
    case Event(CancelTask, _) => stay
  }

  when(Working) {
    case Event(Success(()), TaskInfo(jobQueue, documentSetId)) => {
      jobQueue ! TaskDone(documentSetId, None)
      jobQueue ! ReadyForTask

      goto(Ready) using ExternalActors(jobQueue)
    }
    case Event(Failure(e), TaskInfo(jobQueue, documentSetId)) => {
      // If exceptions are not handled in the task step, the exception is re-thrown in order to kill
      // the worker and have the job rescheduled.
      logger.error("Exception in task, documentSetId={}", documentSetId)
      throw e
    }

    case Event(CancelTask, _)    => stay
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

  private def logDocumentProcessingError(documentSetId: Long, t: Throwable) = {
    logger.info("Exception processing file in DocumentSet {}:", documentSetId, t)
  }
}

object FileGroupTaskWorker {
  def apply(jobQueueActorPath: String,
            fileRemovalQueueActorPath: String,
            fileGroupRemovalQueueActorPath: String,
            bulkDocumentWriter: BulkDocumentWriter): Props =
    Props(new FileGroupTaskWorkerImpl(
      jobQueueActorPath,
      fileRemovalQueueActorPath,
      fileGroupRemovalQueueActorPath,
      bulkDocumentWriter))

  private class FileGroupTaskWorkerImpl(
    jobQueueActorPath: String,
    fileRemovalQueueActorPath: String,
    fileGroupRemovalQueueActorPath: String,
    bulkDocumentWriter: BulkDocumentWriter
  ) extends FileGroupTaskWorker with HasDatabase {

    import context.dispatcher

    override protected val jobQueueSelection = context.actorSelection(jobQueueActorPath)

    private val fileRemovalQueue = context.actorSelection(fileRemovalQueueActorPath)
    private val fileGroupRemovalQueue = context.actorSelection(fileGroupRemovalQueueActorPath)
    private val timeoutGenerator = new TimeoutGenerator(context.system.scheduler, context.dispatcher)
    private val uploadedFileProcessCreator = UploadedFileProcessCreator(bulkDocumentWriter, timeoutGenerator)
    override protected val searchIndex = TransportIndexClient.singleton

    override protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Future[Unit] = {
      for {
        _ <- DocumentSetCreationJobDeleter.deleteByDocumentSet(documentSetId)
        _ <- TempFileDeleter.delete(documentSetId)
        _ <- DocumentSetDeleter.delete(documentSetId)
        _ <- FileGroupDeleter.delete(fileGroupId)
      } yield {
        fileRemovalQueue ! FileRemovalRequestQueueProtocol.RemoveFiles
        fileGroupRemovalQueue ! FileGroupRemovalRequestQueueProtocol.RemoveFileGroup(fileGroupId)
        ()
      }
    }

    protected def processUploadedFile(documentSetId: Long, uploadedFileId: Long,
                                      options: UploadProcessOptions, documentIdSupplier: ActorRef): Future[Unit] = {
      import database.api._
      database.option(GroupedFileUploads.filter(_.id === uploadedFileId)).flatMap(_ match {
        case None => Future.successful(())
        case Some(upload) => {
          val process = uploadedFileProcessCreator.create(upload, options, documentSetId, documentIdSupplier)
          process.start
        }
      })
    }

    override protected def updateDocumentSetInfo(documentSetId: Long): Future[Unit] = {
      documentSetInfoUpdater.update(documentSetId)
    }

    private val documentSetInfoUpdater = DocumentSetInfoUpdater(bulkDocumentWriter)
  }
}
