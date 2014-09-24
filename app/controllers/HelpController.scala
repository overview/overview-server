package controllers

import controllers.auth.OptionallyAuthorizedAction
import controllers.auth.Authorities.anyUser

object HelpController extends Controller {
  def show() = OptionallyAuthorizedAction.inTransaction(anyUser) { implicit request =>
    Ok(views.html.Help.show(request.user))
  }
}
