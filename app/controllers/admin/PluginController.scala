package controllers.admin

import javax.inject.Inject
import play.api.i18n.MessagesApi

import controllers.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser

class PluginController @Inject() (
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  def index = AuthorizedAction(adminUser) { implicit request =>
    Ok(views.html.admin.Plugin.index(request.user))
  }
}
