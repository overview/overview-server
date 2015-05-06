package controllers

import play.api.mvc.Action
import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.DocumentSetFileBackend

trait DocumentSetFileController extends Controller {
  protected val backend: DocumentSetFileBackend

  def head(documentSetId: Long, sha1: Array[Byte]) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    backend.existsByIdAndSha1(documentSetId, sha1).map(_ match {
      case true => NoContent
      case false => NotFound
    })
  }
}

object DocumentSetFileController extends DocumentSetFileController {
  override protected val backend = DocumentSetFileBackend
}
