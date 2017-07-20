package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import controllers.backend.DocumentSetBackend

class PublicDocumentSetController @Inject() (
  documentSetBackend: DocumentSetBackend,
  val controllerComponents: ControllerComponents,
  indexHtml: views.html.PublicDocumentSet.index
) extends BaseController {
  def index = authorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
      documentSets <- documentSetBackend.indexPublic
    } yield Ok(indexHtml(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }
}
