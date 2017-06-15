package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import controllers.backend.DocumentSetBackend

class DocumentCloudProjectController @Inject() (
  documentSetBackend: DocumentSetBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
    } yield Ok(views.html.DocumentCloudProject.index(request.user, count))
  }
}
