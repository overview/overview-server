package controllers.admin

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import models.OverviewDocumentSetCreationJob

object DocumentSetCreationJobController extends Controller {
  def index() = AuthorizedAction(adminUser) { implicit request =>
    val jobs = OverviewDocumentSetCreationJob.all
    Ok(views.html.admin.DocumentSetCreationJob.index(request.user, jobs))
  }
}
