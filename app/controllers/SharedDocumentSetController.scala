package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import controllers.backend.DocumentSetBackend

class SharedDocumentSetController @Inject() (
  documentSetBackend: DocumentSetBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
      documentSets <- documentSetBackend.indexByViewerEmail(request.user.email)
    } yield Ok(views.html.SharedDocumentSet.index(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }
}
