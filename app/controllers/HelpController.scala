package controllers

import play.api.mvc.Controller

import controllers.auth.OptionallyAuthorizedAction
import controllers.auth.Authorities.anyUser
import models.OverviewUser

object HelpController extends Controller {
  def show() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.Help.show(request.user))
  }
}
