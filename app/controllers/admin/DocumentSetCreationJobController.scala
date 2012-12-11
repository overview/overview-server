package controllers.admin

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import models.orm.DocumentSetCreationJob

object DocumentSetCreationJobController extends Controller {
  def index() = AuthorizedAction(adminUser) { implicit request =>
    val jobs = DocumentSetCreationJob.all.iterator.toSeq
    Ok(views.html.admin.DocumentSetCreationJob.index(request.user, jobs))
  }
}
