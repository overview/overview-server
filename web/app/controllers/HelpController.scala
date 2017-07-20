package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi

import controllers.auth.OptionallyAuthorizedAction
import controllers.auth.Authorities.anyUser

class HelpController @Inject() (
  val controllerComponents: ControllerComponents,
  showHtml: views.html.Help.show
) extends BaseController {
  def show() = optionallyAuthorizedAction(anyUser) { implicit request =>
    Ok(showHtml(request.user))
  }
}
