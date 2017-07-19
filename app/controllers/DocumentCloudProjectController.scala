package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import controllers.backend.DocumentSetBackend

class DocumentCloudProjectController @Inject() (
  documentSetBackend: DocumentSetBackend,
  val controllerComponents: ControllerComponents,
  documentCloudProjectIndex: views.html.DocumentCloudProject.index
) extends BaseController {
  def index = authorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
    } yield Ok(documentCloudProjectIndex(request.user, count))
  }
}
