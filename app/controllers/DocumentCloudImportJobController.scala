package controllers

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentSet,DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType}
import com.overviewdocs.models.tables.DocumentSetCreationJobs
import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import controllers.backend.DocumentSetBackend
import controllers.forms.DocumentCloudImportJobForm
import models.DocumentCloudImportJob

trait DocumentCloudImportJobController extends Controller {
  protected val documentSetBackend: DocumentSetBackend

  trait Storage {
    def insertJob(documentSetId: Long, job: DocumentCloudImportJob): Future[Unit]
  }

  def _new(query: String) = AuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.DocumentCloudImportJob._new(request.user, query))
  }

  def create() = AuthorizedAction(anyUser).async { implicit request =>
    val form = DocumentCloudImportJobForm(request.user.email)
    form.bindFromRequest().fold(
      f => Future.successful(BadRequest),
      { job : DocumentCloudImportJob =>
        val attributes = DocumentSet.CreateAttributes(
          title = job.title,
          query = Some(job.query)
        )
        for {
          documentSet <- documentSetBackend.create(attributes, request.user.email)
          _ <- storage.insertJob(documentSet.id, job)
        } yield Redirect(routes.DocumentSetController.show(documentSet.id)).flashing("event" -> "document-set-create")
      }
    )
  }

  val storage : DocumentCloudImportJobController.Storage
}

object DocumentCloudImportJobController extends DocumentCloudImportJobController {
  override protected val documentSetBackend = DocumentSetBackend

  object DatabaseStorage extends Storage with HasDatabase {
    import database.api._

    override def insertJob(documentSetId: Long, job: DocumentCloudImportJob) = {
      database.runUnit(DocumentSetCreationJobs.map(_.createAttributes).+=(DocumentSetCreationJob.CreateAttributes(
        documentSetId=documentSetId,
        jobType=DocumentSetCreationJobType.DocumentCloud,
        retryAttempts=0,
        lang=job.lang,
        splitDocuments=job.splitDocuments,
        documentcloudUsername=job.credentials.map(_.username),
        documentcloudPassword=job.credentials.map(_.password),
        contentsOid=None,
        sourceDocumentSetId=None,
        state=DocumentSetCreationJobState.NotStarted,
        fractionComplete=0,
        statusDescription="",
        canBeCancelled=true
      )))
    }
  }

  override val storage = DatabaseStorage
}
