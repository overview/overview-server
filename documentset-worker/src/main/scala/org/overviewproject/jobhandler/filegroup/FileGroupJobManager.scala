package org.overviewproject.jobhandler.filegroup

import akka.actor.{ Actor, ActorRef }
import org.overviewproject.jobs.models.ClusterFileGroup
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.ClusterFileGroupCommand
import akka.actor.Props
import org.overviewproject.database.Database
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType._

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
  }

  def receive = {

    case ClusterFileGroupCommand(documentSetId, fileGroupId, name, lang, stopWords, importantWords) => {
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
  }

  override protected val storage = new DatabaseStorage
}

object FileGroupJobManager {

  def apply(fileGroupJobQueue: ActorRef, clusteringJobQueue: ActorRef): Props =
    Props(new FileGroupJobManagerImpl(fileGroupJobQueue, clusteringJobQueue))
}