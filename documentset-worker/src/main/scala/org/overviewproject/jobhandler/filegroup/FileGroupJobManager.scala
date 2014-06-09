package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import scala.language.postfixOps
import akka.actor.{ Actor, ActorRef }
import akka.actor.Props
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.DocumentSetCreationJobFinder
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.jobhandler.filegroup.FileGroupJobMessages._
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.util.Logger
import org.overviewproject.util.Configuration

object ClusteringJobQueueProtocol {
  case class ClusterDocumentSet(documentSetId: Long)
}

/**
 * The `FileGroupJobManager` receives a request from the server to extract text from uploaded files
 * and cluster them. It creates the needed sub-tasks and request that each task be performed by a specific
 * job queue, in sequence.
 *
 * If a cancel command is received, the cancellation is passed on to the job queue.
 * The job queue replies with FileGroupDocumentsCreated,regardless of whether a job has been cancelled or completed.
 * If the job has been cancelled, a delete command is sent to the job queue. Otherwise, clustering is started.
 *
 * On startup, any InProgress jobs are restarted.
 *
 */
trait FileGroupJobManager extends Actor {
  import FileGroupJobQueueProtocol._
  import ClusteringJobQueueProtocol._

  private val MaxRetryAttempts = Configuration.getInt("max_job_retry_attempts")
  protected val fileGroupJobQueue: ActorRef
  protected val clusteringJobQueue: ActorRef

  protected val storage: Storage

  private val textExtractionJobsPendingCancellation: mutable.Map[Long, Long] = mutable.Map.empty

  trait Storage {
    def findValidInProgressUploadJobs: Iterable[DocumentSetCreationJob]
    def findValidCancelledUploadJobs: Iterable[DocumentSetCreationJob]
    def updateJobState(documentSetId: Long, jobState: DocumentSetCreationJobState): Option[DocumentSetCreationJob]
    def increaseRetryAttempts(job: DocumentSetCreationJob): Unit
  }

  override def preStart(): Unit = {
    storage.findValidInProgressUploadJobs.foreach { j =>
      if (j.retryAttempts < MaxRetryAttempts) retryJob(j)
      else failJob(j)
    }
    storage.findValidCancelledUploadJobs.foreach { j =>
      cancelJob(j.documentSetId, j.fileGroupId.get)
    }
  }

  def receive = {

    case ClusterFileGroupCommand(documentSetId, fileGroupId, name, lang, stopWords, importantWords) => {
      storage.updateJobState(documentSetId, TextExtractionInProgress).fold(
        Logger.error(s"Trying to cluster non-existent job for document set $documentSetId")) { _ =>
          queueJob(documentSetId, fileGroupId)
        }

    }

    case FileGroupDocumentsCreated(documentSetId) =>
      textExtractionJobsPendingCancellation.get(documentSetId).fold {
        clusteringJobQueue ! ClusterDocumentSet(documentSetId)
      } { fileGroupId =>
        fileGroupJobQueue ! DeleteFileUpload(documentSetId, fileGroupId)
        textExtractionJobsPendingCancellation -= documentSetId
      }

    case CancelClusterFileGroupCommand(documentSetId, fileGroupId) =>
      cancelJob(documentSetId, fileGroupId)

  }

  private def retryJob(job: DocumentSetCreationJob): Unit = {
    storage.increaseRetryAttempts(job)
    queueJob(job.documentSetId, job.fileGroupId.get)
  }
  
  private def failJob(job: DocumentSetCreationJob): Unit = storage.updateJobState(job.documentSetId, Error)

  private def queueJob(documentSetId: Long, fileGroupId: Long): Unit =
    fileGroupJobQueue ! CreateDocumentsFromFileGroup(documentSetId, fileGroupId)

  private def cancelJob(documentSetId: Long, fileGroupId: Long): Unit = {
    textExtractionJobsPendingCancellation += (documentSetId -> fileGroupId)
    fileGroupJobQueue ! CancelFileUpload(documentSetId, fileGroupId)
  }

}

class FileGroupJobManagerImpl(
    override protected val fileGroupJobQueue: ActorRef,
    override protected val clusteringJobQueue: ActorRef) extends FileGroupJobManager {

  class DatabaseStorage extends Storage {

    override def findValidInProgressUploadJobs: Iterable[DocumentSetCreationJob] = Database.inTransaction {
      val jobs = DocumentSetCreationJobFinder.byState(TextExtractionInProgress)

      jobs.filter(_.fileGroupId.isDefined)
    }

    override def findValidCancelledUploadJobs: Iterable[DocumentSetCreationJob] = Database.inTransaction {
      val cancelledUploadJobs = DocumentSetCreationJobFinder.byStateAndType(Cancelled, FileUpload)

      cancelledUploadJobs.filter(_.fileGroupId.isDefined)
    }

    override def updateJobState(documentSetId: Long, jobState: DocumentSetCreationJobState): Option[DocumentSetCreationJob] = Database.inTransaction {
      DocumentSetCreationJobFinder.byDocumentSetAndStateForUpdate(documentSetId, FilesUploaded).headOption.map { job =>
        DocumentSetCreationJobStore.insertOrUpdate(job.copy(state = jobState))
      }
    }

    override def increaseRetryAttempts(job: DocumentSetCreationJob): Unit = Database.inTransaction {
      DocumentSetCreationJobStore.insertOrUpdate(job.copy(retryAttempts = job.retryAttempts + 1))
    }
  }

  override protected val storage = new DatabaseStorage
}

object FileGroupJobManager {

  def apply(fileGroupJobQueue: ActorRef, clusteringJobQueue: ActorRef): Props =
    Props(new FileGroupJobManagerImpl(fileGroupJobQueue, clusteringJobQueue))
}