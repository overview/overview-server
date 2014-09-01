package controllers.api

import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DbDocumentBackend,DocumentBackend}

trait DocumentController extends ApiController {
  protected val backend: DocumentBackend

  def index(documentSetId: Long, q: String) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    backend.index(documentSetId, q).map { documents =>
      Ok(views.json.api.DocumentInfo.index(documents))
    }
  }

  def show(documentSetId: Long, documentId: Long) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    backend.show(documentSetId, documentId).map(_ match {
      case Some(document) => Ok(views.json.api.Document.show(document))
      case None => NotFound(jsonError(s"Document $documentId not found in document set $documentSetId"))
    })
  }
}

object DocumentController extends DocumentController {
  override protected val backend = DocumentBackend
}
