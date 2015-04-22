package controllers

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import controllers.backend.DocumentSetBackend
import controllers.forms.DocumentCloudImportJobForm
import models.{DocumentCloudImportJob,OverviewDatabase}
import models.orm.stores.DocumentCloudImportJobStore
import org.overviewproject.models.DocumentSet

trait DocumentCloudImportJobController extends Controller {
  protected val documentSetBackend: DocumentSetBackend

  trait Storage {
    def insertJob(documentSetId: Long, job: DocumentCloudImportJob): Future[Unit]
  }

  def new_(query: String) = AuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.DocumentCloudImportJob.new_(request.user, query))
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
        } yield Redirect(routes.DocumentSetController.index()).flashing("event" -> "document-set-create")
      }
    )
  }

  val storage : DocumentCloudImportJobController.Storage
}

object DocumentCloudImportJobController extends DocumentCloudImportJobController {
  override protected val documentSetBackend = DocumentSetBackend

  object DatabaseStorage extends Storage {
    override def insertJob(documentSetId: Long, job: DocumentCloudImportJob) = Future(OverviewDatabase.inTransaction {
      DocumentCloudImportJobStore.insert(documentSetId, job)
    })
  }

  override val storage = DatabaseStorage
}
