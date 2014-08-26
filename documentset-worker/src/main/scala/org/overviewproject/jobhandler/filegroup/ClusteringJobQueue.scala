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

      /**
       * Creates a new clustering job and deletes the old document creation job.
       * If the document creation job has been cancelled, nothing happens, and we assume the cancellation 
       * code deletes everything properly.
       * When the user cancels the job, it's possible that the server may not see either of the jobs. In this case
       * the server assumes it is just deleting a document set. The Document set deletion code will try to cancel 
       * the clustering job, if it detects the job before starting to delete the document set.
       */
      override def transitionToClusteringJob(documentSetId: Long): Unit = Database.inTransaction {
        val documentSetFinder = new FinderById(documentSets)

        for {
          createDocumentsJob <- DocumentSetCreationJobFinder.byDocumentSetAndTypeForUpdate(documentSetId, FileUpload).headOption
          if createDocumentsJob.state != Cancelled
          documentSet <- documentSetFinder.byId(documentSetId).headOption
        } {
          val clusteringJob = DocumentSetCreationJob(
            documentSetId = documentSet.id,
            treeTitle = Some(documentSet.title),
            jobType = Recluster,
            suppliedStopWords = createDocumentsJob.suppliedStopWords,
            importantWords = createDocumentsJob.importantWords,
            splitDocuments = createDocumentsJob.splitDocuments,
            state = NotStarted)
          DocumentSetCreationJobStore.insertOrUpdate(clusteringJob)

          DocumentSetCreationJobStore.deleteById(createDocumentsJob.id)
        }
      }
    }
  }
}