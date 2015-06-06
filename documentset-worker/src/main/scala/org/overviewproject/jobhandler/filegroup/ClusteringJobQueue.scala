package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.Props
import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._
import org.overviewproject.jobhandler.filegroup.ClusteringJobQueueProtocol.ClusterDocumentSet
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.database.orm.Schema.{ documentSets, fileGroups }
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.database.orm.finders.DocumentSetCreationJobFinder
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.finders.FinderById
import org.overviewproject.tree.orm.stores.BaseStore

trait ClusteringJobQueue extends Actor {

  def receive = {
    case ClusterDocumentSet(documentSetId) => {
      storage.transitionToClusteringJob(documentSetId).map { fileGroupId =>
        fileGroupRemovalRequestQueue ! RemoveFileGroup(fileGroupId)
      }
    }
  }

  protected val fileGroupRemovalRequestQueue: ActorSelection
  protected val storage: Storage

  protected trait Storage {
    def transitionToClusteringJob(documentSetId: Long): Option[Long]
  }
}

object ClusteringJobQueue {

  def apply(fileGroupRemovalRequestQueuePath: String): Props =
    Props(new ClusteringJobQueueImpl(fileGroupRemovalRequestQueuePath))

  class ClusteringJobQueueImpl(fileGroupRemovalRequestQueuePath: String) extends ClusteringJobQueue {
    override protected val fileGroupRemovalRequestQueue = context.actorSelection(fileGroupRemovalRequestQueuePath)
    override protected val storage: Storage = new DatabaseStorage

    protected class DatabaseStorage extends Storage {

      /**
       * Creates a new clustering job and deletes the old document creation job.
       * If the document creation job has been cancelled, nothing happens, and we assume the cancellation
       * code deletes everything properly.
       * When the user cancels the job, it's possible that the server may not see either of the jobs. In this case
       * the server assumes it is just deleting a document set. The Document set deletion code will try to cancel
       * the clustering job, if it detects the job before starting to delete the document set.
       *
       * Returns the fileGroupId of the job, because it's needed.
       *
       * FIXME: All of it. Mainly we should not communicate via DocumentSetCreationJobs in the database
       */
      override def transitionToClusteringJob(documentSetId: Long): Option[Long] = DeprecatedDatabase.inTransaction {
        val documentSetFinder = new FinderById(documentSets)

        for {
          createDocumentsJob <- DocumentSetCreationJobFinder.byDocumentSetAndTypeForUpdate(documentSetId, FileUpload).headOption
          if createDocumentsJob.state != Cancelled
          documentSet <- documentSetFinder.byId(documentSetId).headOption
        } yield {

          val fileGroupId = createDocumentsJob.fileGroupId.get

          val clusteringJob = DocumentSetCreationJob(
            documentSetId = documentSet.id,
            treeTitle = Some("Tree"), // FIXME translate by creating this job somewhere else
            jobType = Recluster,
            lang = createDocumentsJob.lang,
            suppliedStopWords = createDocumentsJob.suppliedStopWords,
            importantWords = createDocumentsJob.importantWords,
            splitDocuments = createDocumentsJob.splitDocuments,
            state = NotStarted)
          DocumentSetCreationJobStore.insertOrUpdate(clusteringJob)

          deleteFileGroup(createDocumentsJob)
          DocumentSetCreationJobStore.deleteById(createDocumentsJob.id)

          fileGroupId
        }
      }
    }

    private def deleteFileGroup(job: DocumentSetCreationJob) = {
      val fileGroupFinder = new FinderById(fileGroups)
      val fileGroupStore = new BaseStore(fileGroups)

      for {
        fileGroupId <- job.fileGroupId
        fileGroup <- fileGroupFinder.byId(fileGroupId).headOption
      } yield fileGroupStore.insertOrUpdate(fileGroup.copy(deleted = true))
    }
  }
}
