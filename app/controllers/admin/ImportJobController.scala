package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.Authorities.adminUser
import controllers.auth.AuthorizedAction
import controllers.backend.ImportJobBackend
import controllers.util.DocumentSetDeletionComponents
import controllers.Controller
import models.{OverviewDatabase,User}
import models.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.database.HasBlockingDatabase
import org.overviewproject.models.{DocumentSet,DocumentSetCreationJob}
import org.overviewproject.models.tables.{DocumentSetCreationJobs,DocumentSets}
import org.overviewproject.jobs.models.{CancelFileUpload,Delete}
import org.overviewproject.tree.orm.{DocumentSetCreationJob=>DeprecatedDocumentSetCreationJob}
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.orm.DocumentSetCreationJobState

trait ImportJobController extends Controller {
  protected val storage: ImportJobController.Storage
  protected val jobQueue: ImportJobController.JobMessageQueue
  protected val importJobBackend: ImportJobBackend

  def index() = AuthorizedAction(adminUser).async { implicit request =>
    for {
      jobs: Seq[(DocumentSetCreationJob,DocumentSet,Option[String])] <- importJobBackend.indexWithDocumentSetsAndUsers
    } yield Ok(views.html.admin.ImportJob.index(request.user, jobs))
  }

  def delete(importJobId: Long) = AuthorizedAction(adminUser).async { implicit request =>
    // XXX This is all copy/pasted from DocumentSetController
    // FIXME: Move all deletion to worker and remove database access here
    // FIXME: Make client distinguish between deleting document sets and canceling jobs
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")

    def done(message: String) = Redirect(routes.ImportJobController.index()).flashing(
      "success" -> m(message),
      "event" -> "document-set-delete"
    )

    implicit class MaybeCancelledJob(maybeJob: Option[DeprecatedDocumentSetCreationJob]) {
      def documentSetId = maybeJob.map(_.id).getOrElse(throw new RuntimeException("Invalid access of document set id"))
      def doesNotExist: Boolean = maybeJob.isEmpty
      def wasTextExtractionJob: Boolean = maybeJob.flatMap(_.fileGroupId).isDefined
      def wasRunningInWorker: Boolean = (
        maybeJob.map(_.state) == Some(DocumentSetCreationJobState.InProgress)
        && maybeJob.map(_.jobType) != Some(DocumentSetCreationJobType.Recluster)
      )
      def wasNotRunning: Boolean = (
        maybeJob.map(_.state) == Some(DocumentSetCreationJobState.NotStarted)
        || maybeJob.map(_.state) == Some(DocumentSetCreationJobState.Error)
        || maybeJob.map(_.state) == Some(DocumentSetCreationJobState.Cancelled)
      )
      def wasRunningInTextExtractionWorker: Boolean = (
        maybeJob.map(_.state) == Some(DocumentSetCreationJobState.FilesUploaded)
        || maybeJob.map(_.state) == Some(DocumentSetCreationJobState.TextExtractionInProgress)
      )
    }

    storage.findDocumentSetIdByJobId(importJobId).map(_ match {
      case None => done("deleteJob.success")
      case Some(documentSetId) => {
        // FIXME: If a reclustering job is running, but there are failed jobs, we assume
        // that the delete refers to canceling the running job.
        // It would be better for the client to explicitly tell us what job to cancel, rather
        // than trying to guess.
        val cancelledJob: Option[DeprecatedDocumentSetCreationJob] = storage.cancelJob(documentSetId)

        if (cancelledJob.doesNotExist) {
          storage.deleteDocumentSet(documentSetId)
          jobQueue.send(Delete(documentSetId))
          
          done("deleteDocumentSet.success")
        } else if (cancelledJob.wasRunningInWorker) {
          storage.deleteDocumentSet(documentSetId)
          jobQueue.send(Delete(documentSetId, waitForJobRemoval = true)) // wait for worker to stop clustering and remove job
          done("deleteJob.success")
        } else if (cancelledJob.wasNotRunning) {
          storage.deleteDocumentSet(documentSetId)
          jobQueue.send(Delete(documentSetId, waitForJobRemoval = false)) // don't wait for worker
          done("deleteJob.success")
        } else if (cancelledJob.wasRunningInTextExtractionWorker && cancelledJob.wasTextExtractionJob) {
          jobQueue.send(CancelFileUpload(documentSetId, cancelledJob.get.fileGroupId.get))
          done("deleteJob.success")
        } else {
          throw new RuntimeException("A job was in a state we do not handle")
        }
      }
    })
  }

  private def flashSuccess = Redirect(routes.ImportJobController.index())
    .flashing("success" -> "The document set and the job that spawned it have been deleted.")

  private def flashFailure = Redirect(routes.ImportJobController.index())
    .flashing("warning" -> "Could not delete job: it does not exist. Has it completed?")

}

object ImportJobController extends ImportJobController with DocumentSetDeletionComponents {
  trait Storage {
    def findDocumentSetIdByJobId(jobId: Long): Future[Option[Long]]
    def cancelJob(documentSetId: Long): Option[DeprecatedDocumentSetCreationJob]
    def deleteDocumentSet(documentSetId: Long): Unit
  }

  trait JobMessageQueue {
    def send(deleteCommand: Delete): Unit
    def send(cancelFileUploadCommand: CancelFileUpload): Unit
  }

  object DatabaseStorage extends Storage with HasBlockingDatabase {
    import database.api._

    override def cancelJob(documentSetId: Long): Option[DeprecatedDocumentSetCreationJob] = {
      OverviewDatabase.inTransaction {
        DocumentSetCreationJobStore.findCancellableJobByDocumentSetAndCancel(documentSetId)
      }
    }

    override def deleteDocumentSet(documentSetId: Long) = {
      blockingDatabase.runUnit(
        DocumentSets
          .filter(_.id === documentSetId)
          .map(_.deleted).update(true)
      )
    }

    override def findDocumentSetIdByJobId(importJobId: Long) = database.option(
      DocumentSetCreationJobs.filter(_.id === importJobId).map(_.documentSetId)
    )
  }

  object ApolloJobMessageQueue extends JobMessageQueue with DocumentSetDeletionJobMessageQueue 

  override protected val storage = DatabaseStorage
  override protected val jobQueue = ApolloJobMessageQueue
  override protected val importJobBackend = ImportJobBackend
}
