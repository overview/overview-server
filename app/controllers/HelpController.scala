package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi

import controllers.auth.OptionallyAuthorizedAction
import controllers.auth.Authorities.anyUser

class HelpController @Inject() (
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  def show() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.Help.show(request.user))
  }
}
