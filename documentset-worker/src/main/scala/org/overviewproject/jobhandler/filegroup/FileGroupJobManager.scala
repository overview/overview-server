package org.overviewproject.jobhandler.filegroup

import scala.concurrent.duration._
import scala.collection.mutable
import scala.language.postfixOps
import akka.actor.{ Actor, ActorRef }
import akka.actor.Props
import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.database.orm.finders.DocumentSetCreationJobFinder
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.jobhandler.filegroup.FileGroupJobMessages._
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.util.Logger
import org.overviewproject.util.Configuration
import org.overviewproject.jobhandler.filegroup.task.UploadProcessOptions

object ClusteringJobQueueProtocol {
  case class ClusterDocumentSet(documentSetId: Long)
}

/**
 * The `FileGroupJobManager` receives a request from the server to extract text from uploaded files
 * and cluster them. It creates the needed sub-job and request that each job be performed by a specific
 * job queue, in sequence.
 *
 * If a cancel command is received, the cancellation is passed on to the job queue.
 * The job queue replies with `JobCompleted`,regardless of whether a job has been cancelled or completed.
 * If the job has been cancelled, a delete command is sent to the job queue. Otherwise, clustering is started.
 *
 * On startup, any InProgress jobs are restarted.
 *
 */
trait FileGroupJobManager extends Actor {
  import FileGroupJobQueueProtocol._
  import ClusteringJobQueueProtocol._

  private lazy val logger = Logger.forClass(getClass)

  private val MaxRetryAttempts = Configuration.getInt("max_job_retry_attempts")
  protected val fileGroupJobQueue: ActorRef
  protected val clusteringJobQueue: ActorRef

  protected val storage: FileGroupJobManager.Storage

  private val runningJobs: mutable.Map[Long, FileGroupJob] = mutable.Map.empty
  private val cancellingJobs: mutable.Map[Long, Long] = mutable.Map.empty

  private case class UpdateJobStateRetryAttempt(command: ClusterFileGroupCommand, count: Int)

  override def preStart(): Unit = {
    storage.findValidInProgressUploadJobs.foreach { j =>
      if (j.retryAttempts < MaxRetryAttempts) retryJob(j)
      else failJob(j)
    }
    storage.findValidCancelledUploadJobs.foreach { j =>
      cancelJob(j.documentSetId, j.fileGroupId.get)
    }
  }

  override def receive = {

    case command: ClusterFileGroupCommand           => attemptUpdateJobState(command, 0)

    case UpdateJobStateRetryAttempt(command, count) => attemptUpdateJobState(command, count)

    case JobCompleted(documentSetId)                => handleCompletedJob(documentSetId)

    case CancelClusterFileGroupCommand(documentSetId, fileGroupId) =>
      cancelJob(documentSetId, fileGroupId)

  }

  private def attemptUpdateJobState(command: ClusterFileGroupCommand, count: Int): Unit = {
    storage.updateJobState(command.documentSetId).map(queueCreateDocumentsJob)
      .getOrElse(limitedRetryUpdateJobState(command, count))
  }

  private def limitedRetryUpdateJobState(command: ClusterFileGroupCommand, count: Int): Unit = {
    if (count < MaxRetryAttempts) retryUpdateJobState(command, count + 1)
    else logger.error("Trying to cluster non-existent job for document set {}", command.documentSetId)
  }

  private def retryUpdateJobState(command: ClusterFileGroupCommand, attempt: Int): Unit = {
    import context.dispatcher
    context.system.scheduler.scheduleOnce(1 seconds, self, UpdateJobStateRetryAttempt(command, attempt))
  }

  private def retryJob(job: DocumentSetCreationJob): Unit = {
    storage.increaseRetryAttempts(job)
    queueCreateDocumentsJob(job)
  }

  private def failJob(job: DocumentSetCreationJob): Unit = storage.failJob(job)

  private def queueCreateDocumentsJob(job: DocumentSetCreationJob): Unit =
    submitToFileGroupJobQueue(job.documentSetId,
      CreateDocumentsJob(job.fileGroupId.get, UploadProcessOptions(job.lang, job.splitDocuments)))

  private def submitToFileGroupJobQueue(documentSetId: Long, job: FileGroupJob): Unit = {
    runningJobs += (documentSetId -> job)
    fileGroupJobQueue ! SubmitJob(documentSetId, job)
  }

  private def handleCompletedJob(documentSetId: Long) = runningJobs.get(documentSetId).map {
    case CreateDocumentsJob(fileGroupId, splitDocuments) => submitClusteringJob(documentSetId)
    case DeleteFileGroupJob(fileGroupId)                 =>
  } getOrElse deleteCancelledJobFileGroup(documentSetId)

  private def submitClusteringJob(documentSetId: Long): Unit =
    clusteringJobQueue ! ClusterDocumentSet(documentSetId)

  private def cancelJob(documentSetId: Long, fileGroupId: Long): Unit = {
    runningJobs -= documentSetId
    cancellingJobs += (documentSetId -> fileGroupId)
    fileGroupJobQueue ! CancelFileUpload(documentSetId, fileGroupId)
  }

  private def deleteCancelledJobFileGroup(documentSetId: Long) = {
    cancellingJobs.remove(documentSetId).map { fileGroupId =>
      submitToFileGroupJobQueue(documentSetId, DeleteFileGroupJob(fileGroupId))
    }
  }
}

class FileGroupJobManagerImpl(
  override protected val fileGroupJobQueue: ActorRef,
  override protected val clusteringJobQueue: ActorRef) extends FileGroupJobManager {

  class DatabaseStorage extends FileGroupJobManager.Storage {

    override def findValidInProgressUploadJobs: Iterable[DocumentSetCreationJob] = DeprecatedDatabase.inTransaction {
      val jobs = DocumentSetCreationJobFinder.byState(TextExtractionInProgress)

      jobs.filter(_.fileGroupId.isDefined)
    }

    override def findValidCancelledUploadJobs: Iterable[DocumentSetCreationJob] = DeprecatedDatabase.inTransaction {
      val cancelledUploadJobs = DocumentSetCreationJobFinder.byStateAndType(Cancelled, FileUpload)

      cancelledUploadJobs.filter(_.fileGroupId.isDefined)
    }

    override def updateJobState(documentSetId: Long): Option[DocumentSetCreationJob] = DeprecatedDatabase.inTransaction {
      DocumentSetCreationJobFinder.byDocumentSetAndStateForUpdate(documentSetId, FilesUploaded).headOption.map { job =>
        DocumentSetCreationJobStore.insertOrUpdate(job.copy(state = TextExtractionInProgress))
      }
    }

    override def failJob(job: DocumentSetCreationJob): Unit = DeprecatedDatabase.inTransaction {
      DocumentSetCreationJobStore.insertOrUpdate(job.copy(state = Error, statusDescription = "max_retry_attempts"))
    }

    override def increaseRetryAttempts(job: DocumentSetCreationJob): Unit = DeprecatedDatabase.inTransaction {
      DocumentSetCreationJobStore.insertOrUpdate(job.copy(retryAttempts = job.retryAttempts + 1))
    }
  }

  override protected val storage = new DatabaseStorage
}

object FileGroupJobManager {
  trait Storage {
    def findValidInProgressUploadJobs: Iterable[DocumentSetCreationJob]
    def findValidCancelledUploadJobs: Iterable[DocumentSetCreationJob]
    def updateJobState(documentSetId: Long): Option[DocumentSetCreationJob]
    def failJob(job: DocumentSetCreationJob): Unit
    def increaseRetryAttempts(job: DocumentSetCreationJob): Unit
  }

  def apply(fileGroupJobQueue: ActorRef, clusteringJobQueue: ActorRef): Props =
    Props(new FileGroupJobManagerImpl(fileGroupJobQueue, clusteringJobQueue))
}
