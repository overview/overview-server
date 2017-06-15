package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import controllers.backend.DocumentSetBackend

class PublicDocumentSetController @Inject() (
  documentSetBackend: DocumentSetBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
      documentSets <- documentSetBackend.indexPublic
    } yield Ok(views.html.PublicDocumentSet.index(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }
}
