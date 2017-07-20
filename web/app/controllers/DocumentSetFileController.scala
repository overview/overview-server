package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Action

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.DocumentSetFileBackend

class DocumentSetFileController @Inject() (
  backend: DocumentSetFileBackend,
  val controllerComponents: ControllerComponents
) extends BaseController {
  def head(documentSetId: Long, sha1: Array[Byte]) = authorizedAction(userOwningDocumentSet(documentSetId)).async {
    backend.existsByIdAndSha1(documentSetId, sha1).map(_ match {
      case true => NoContent
      case false => NotFound
    })
  }
}
