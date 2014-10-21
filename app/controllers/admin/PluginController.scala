package controllers.admin

import controllers.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser

trait PluginController extends Controller {
  def index = AuthorizedAction(adminUser) { implicit request =>
    Ok(views.html.admin.Plugin.index(request.user))
  }
}

object PluginController extends PluginController
