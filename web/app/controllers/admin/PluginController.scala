package controllers.admin

import javax.inject.Inject
import play.api.i18n.MessagesApi

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser

class PluginController @Inject() (
  val controllerComponents: controllers.ControllerComponents,
  pluginIndexHtml: views.html.admin.Plugin.index
) extends controllers.BaseController {
  def index = authorizedAction(adminUser) { implicit request =>
    Ok(pluginIndexHtml(request.user))
  }
}
