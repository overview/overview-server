package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import controllers.backend.DocumentSetBackend

trait SharedDocumentSetController extends Controller {
  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
      documentSets <- documentSetBackend.indexByViewerEmail(request.user.email)
    } yield Ok(views.html.SharedDocumentSet.index(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected val documentSetBackend: DocumentSetBackend
}

object SharedDocumentSetController extends SharedDocumentSetController {
  override protected val documentSetBackend = DocumentSetBackend
}
