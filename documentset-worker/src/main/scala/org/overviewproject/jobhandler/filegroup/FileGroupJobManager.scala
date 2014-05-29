package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable
import scala.language.postfixOps
import akka.actor.{ Actor, ActorRef }
import akka.actor.Props
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.DocumentSetCreationJobFinder
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.ClusterFileGroupCommand
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.CancelClusterFileGroupCommand

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

  protected val fileGroupJobQueue: ActorRef
  protected val clusteringJobQueue: ActorRef

  protected val storage: Storage

  private val textExtractionJobsPendingCancellation: mutable.Map[Long, Long] = mutable.Map.empty

  trait Storage {
    def findInProgressJobInformation: Iterable[(Long, Long)]
    def findCancelledJobInformation: Iterable[(Long, Long)]
    def updateJobState(documentSetId: Long): Unit
  }

  override def preStart(): Unit = {
    storage.findInProgressJobInformation.foreach {
      queueJob _ tupled
    }
    storage.findCancelledJobInformation.foreach {
      cancelJob _ tupled
    }
  }

  def receive = {

    case ClusterFileGroupCommand(documentSetId, fileGroupId, name, lang, stopWords, importantWords) => {
      storage.updateJobState(documentSetId)

      queueJob(documentSetId, fileGroupId)
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

    override def findInProgressJobInformation: Iterable[(Long, Long)] = Database.inTransaction {
      val jobs = DocumentSetCreationJobFinder.byState(TextExtractionInProgress)

      for {
        j <- jobs
        fileGroupId <- j.fileGroupId
      } yield (j.documentSetId, fileGroupId)
    }

    override def findCancelledJobInformation: Iterable[(Long, Long)] = Database.inTransaction {
      val cancelledUploadJobs = DocumentSetCreationJobFinder.byStateAndType(Cancelled, FileUpload)
      for {
        j <- cancelledUploadJobs
        fileGroupId <- j.fileGroupId
      } yield (j.documentSetId, fileGroupId)
    }

    override def updateJobState(documentSetId: Long): Unit = Database.inTransaction {
      DocumentSetCreationJobFinder.byDocumentSetAndState(documentSetId, FilesUploaded).map { job =>
        DocumentSetCreationJobStore.insertOrUpdate(job.copy(state = TextExtractionInProgress))
      }
    }
  }

  override protected val storage = new DatabaseStorage
}

object FileGroupJobManager {

  def apply(fileGroupJobQueue: ActorRef, clusteringJobQueue: ActorRef): Props =
    Props(new FileGroupJobManagerImpl(fileGroupJobQueue, clusteringJobQueue))
}