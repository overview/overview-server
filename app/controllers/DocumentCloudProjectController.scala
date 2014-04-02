package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser

trait DocumentCloudProjectController extends Controller {
  def index = AuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.DocumentCloudProject.index(request.user))
  }
}

object DocumentCloudProjectController extends DocumentCloudProjectController
