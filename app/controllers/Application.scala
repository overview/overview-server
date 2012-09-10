package controllers

import play.api.mvc.{Action,Controller}

object Application  extends Controller with HttpsEnforcer {
  def index() = HttpsAction { implicit request =>
    Redirect(routes.DocumentSetController.index())
  }
}
