package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import controllers.backend.DocumentSetBackend

trait PublicDocumentSetController extends Controller {
  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
      documentSets <- documentSetBackend.indexPublic
    } yield Ok(views.html.PublicDocumentSet.index(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected val documentSetBackend: DocumentSetBackend
}

object PublicDocumentSetController extends PublicDocumentSetController {
  override protected val documentSetBackend = DocumentSetBackend
}
