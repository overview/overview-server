package controllers

import java.util.UUID
import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Action
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{anyUser,adminUser}
import controllers.backend.PluginBackend
import controllers.forms.{PluginCreateForm,PluginUpdateForm}

class PluginController @Inject() (
  backend: PluginBackend,
  val controllerComponents: ControllerComponents
) extends BaseController {
  def index = authorizedAction(anyUser).async {
    for { plugins <- backend.index }
    yield Ok(views.json.Plugin.index(plugins))
  }

  def create = authorizedAction(adminUser).async { implicit request =>
    PluginCreateForm().bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest),
      attributes => {
        for { plugin <- backend.create(attributes) }
        yield Ok(views.json.Plugin.show(plugin))
      }
    )
  }

  def update(id: UUID) = authorizedAction(adminUser).async { implicit request =>
    PluginUpdateForm().bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest),
      attributes => {
        backend.update(id, attributes)
          .map( _ match {
            case Some(plugin) => Ok(views.json.Plugin.show(plugin))
            case None => NotFound
          })
      }
    )
  }

  def destroy(id: UUID) = authorizedAction(adminUser).async {
    backend.destroy(id).map((Unit) => Ok)
  }
}
