package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import org.overviewproject.jobhandler.filegroup.ClusteringJobQueueProtocol.ClusterDocumentSet
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol.StartClustering
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.database.Database
import org.overviewproject.database.orm.Schema.documentSets
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.database.orm.finders.DocumentSetCreationJobFinder
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.database.orm.finders.FinderById

trait ClusteringJobQueue extends Actor {

  def receive = {
    case ClusterDocumentSet(documentSetId) => storage.transitionToClusteringJob(documentSetId)
  }

  protected val storage: Storage

  protected trait Storage {
    def transitionToClusteringJob(documentSetId: Long): Unit
  }
}

object ClusteringJobQueue {

  def apply(): Props = Props(new ClusteringJobQueueImpl)

  class ClusteringJobQueueImpl extends ClusteringJobQueue {

    override protected val storage: Storage = new DatabaseStorage

    protected class DatabaseStorage extends Storage {

      override def transitionToClusteringJob(documentSetId: Long): Unit = Database.inTransaction {
        val documentSetFinder = new FinderById(documentSets)

        val documentSet = documentSetFinder.byId(documentSetId).headOption

        documentSet.map { ds =>
          val clusteringJob = DocumentSetCreationJob(
            documentSetId = ds.id,
            treeTitle = Some(ds.title),
            jobType = Recluster,
            state = NotStarted)
          DocumentSetCreationJobStore.insertOrUpdate(clusteringJob)
        }

        DocumentSetCreationJobStore.delete(DocumentSetCreationJobFinder.byDocumentSetAndType(documentSetId, FileUpload).toQuery)
      }

    }
  }
}