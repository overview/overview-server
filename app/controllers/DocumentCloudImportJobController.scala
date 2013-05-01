package controllers

import play.api.mvc.Controller

import controllers.auth.{ Authorities, AuthorizedAction }
import controllers.forms.DocumentCloudImportJobForm
import models.{ DocumentCloudCredentials, DocumentCloudImportJob }
import models.orm.stores.DocumentCloudImportJobStore

trait DocumentCloudImportJobController extends Controller {
  import Authorities._

  trait Storage {
    def insertJob(job: DocumentCloudImportJob) : Unit
  }

  def new_(dcId: String) = AuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.DocumentCloudImportJob.new_(request.user, dcId))
  }

  def create() = AuthorizedAction(anyUser) { implicit request =>
    val form = DocumentCloudImportJobForm(request.user.email)
    form.bindFromRequest().fold(
      f => BadRequest,
      { job : DocumentCloudImportJob =>
        storage.insertJob(job)
        Redirect(routes.DocumentSetController.index()).flashing(
          "event" -> "document-set-create"
        )
      }
    )
  }

  val storage : DocumentCloudImportJobController.Storage
}

object DocumentCloudImportJobController extends DocumentCloudImportJobController {
  object DatabaseStorage extends Storage {
    override def insertJob(job: DocumentCloudImportJob) = {
      DocumentCloudImportJobStore.insert(job)
    }
  }

  override val storage = DatabaseStorage
}
