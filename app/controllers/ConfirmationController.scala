package controllers

import play.api.mvc.{Action,Controller}

object ConfirmationController extends Controller {
  def show(token: String) = Action { Redirect(routes.Application.index()) }
}
