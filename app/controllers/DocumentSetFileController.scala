package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.DocumentSetFileBackend

class DocumentSetFileController @Inject() (
  backend: DocumentSetFileBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  def head(documentSetId: Long, sha1: Array[Byte]) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    backend.existsByIdAndSha1(documentSetId, sha1).map(_ match {
      case true => NoContent
      case false => NotFound
    })
  }
}
