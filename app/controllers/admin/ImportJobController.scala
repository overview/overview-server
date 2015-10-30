package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import com.overviewdocs.database.{DeprecatedDatabase,HasBlockingDatabase}
import com.overviewdocs.models.{DocumentSet,DocumentSetCreationJob}
import com.overviewdocs.models.tables.{DocumentSetCreationJobs,DocumentSets}
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.tree.orm.{DocumentSetCreationJob=>DeprecatedDocumentSetCreationJob}
import com.overviewdocs.tree.DocumentSetCreationJobType
import com.overviewdocs.tree.orm.DocumentSetCreationJobState
import controllers.auth.Authorities.adminUser
import controllers.auth.AuthorizedAction
import controllers.backend.ImportJobBackend
import controllers.util.JobQueueSender
import controllers.Controller
import models.orm.stores.DocumentSetCreationJobStore

trait ImportJobController extends Controller {
  protected val storage: ImportJobController.Storage
  protected val jobQueue: JobQueueSender
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
        storage.cancelJob(documentSetId)
        jobQueue.send(DocumentSetCommands.CancelJob(documentSetId, importJobId))
        done("deleteJob.success")
      }
    })
  }

  private def flashSuccess = Redirect(routes.ImportJobController.index())
    .flashing("success" -> "The document set and the job that spawned it have been deleted.")

  private def flashFailure = Redirect(routes.ImportJobController.index())
    .flashing("warning" -> "Could not delete job: it does not exist. Has it completed?")

}

object ImportJobController extends ImportJobController {
  trait Storage {
    def findDocumentSetIdByJobId(jobId: Long): Future[Option[Long]]
    def cancelJob(documentSetId: Long): Option[DeprecatedDocumentSetCreationJob]
    def deleteDocumentSet(documentSetId: Long): Unit
  }

  object DatabaseStorage extends Storage with HasBlockingDatabase {
    import database.api._

    override def cancelJob(documentSetId: Long): Option[DeprecatedDocumentSetCreationJob] = {
      DeprecatedDatabase.inTransaction {
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

  override protected val storage = DatabaseStorage
  override protected val jobQueue = JobQueueSender
  override protected val importJobBackend = ImportJobBackend
}
