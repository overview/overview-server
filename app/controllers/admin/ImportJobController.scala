package controllers.admin

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import models.orm.finders.DocumentSetCreationJobFinder
import models.orm.stores.DocumentSetStore

object ImportJobController extends Controller {
  def index() = AuthorizedAction(adminUser) { implicit request =>
    val jobs = DocumentSetCreationJobFinder.all.withDocumentSetsAndOwners
    Ok(views.html.admin.ImportJob.index(request.user, jobs))
  }

  def delete(importJobId: Long) = AuthorizedAction(adminUser) { implicit request =>
    DocumentSetCreationJobFinder.byDocumentSetCreationJob(importJobId).headOption match {
      case Some(job) =>
        DocumentSetStore.deleteOrCancelJob(job.documentSetId)
        Redirect(routes.ImportJobController.index())
          .flashing("success" -> "The document set and the job that spawned it have been deleted.")
      case None =>
        Redirect(routes.ImportJobController.index())
          .flashing("warning" -> "Could not delete job: it does not exist. Has it completed?")
    }
  }
}
