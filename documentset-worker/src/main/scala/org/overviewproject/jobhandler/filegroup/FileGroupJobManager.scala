package org.overviewproject.jobhandler.filegroup

import akka.actor.{ Actor, ActorRef }
import akka.actor.Props
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.DocumentSetCreationJobFinder
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.ClusterFileGroupCommand
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore

object ClusteringJobQueueProtocol {
  case class ClusterDocumentSet(documentSetId: Long)
}

/**
 * The `FileGroupJobManager` receives a request from the server to extract text from uploaded files
 * and cluster them. It creates the needed sub-tasks and request that each task be performed by a specific
 * job queue, in sequence.
 */
trait FileGroupJobManager extends Actor {
  import FileGroupJobQueueProtocol._
  import ClusteringJobQueueProtocol._

  protected val fileGroupJobQueue: ActorRef
  protected val clusteringJobQueue: ActorRef

  protected val storage: Storage

  trait Storage {
    def updateJobState(documentSetId: Long): Unit
  }

  def receive = {

    case ClusterFileGroupCommand(documentSetId, fileGroupId, name, lang, stopWords, importantWords) => {
      storage.updateJobState(documentSetId)
      fileGroupJobQueue ! CreateDocumentsFromFileGroup(documentSetId, fileGroupId)
    }

    case FileGroupDocumentsCreated(fileGroupId) =>
      clusteringJobQueue ! ClusterDocumentSet(fileGroupId)

  }

}

class FileGroupJobManagerImpl(
    override protected val fileGroupJobQueue: ActorRef,
    override protected val clusteringJobQueue: ActorRef) extends FileGroupJobManager {

  class DatabaseStorage extends Storage {
    override def updateJobState(documentSetId: Long): Unit = Database.inTransaction {
      val job = DocumentSetCreationJobFinder.byDocumentSetAndState(documentSetId, FilesUploaded).headOption.get
      DocumentSetCreationJobStore.insertOrUpdate(job.copy(state = TextExtractionInProgress))
    }
  }

  override protected val storage = new DatabaseStorage
}

object FileGroupJobManager {

  def apply(fileGroupJobQueue: ActorRef, clusteringJobQueue: ActorRef): Props =
    Props(new FileGroupJobManagerImpl(fileGroupJobQueue, clusteringJobQueue))
}