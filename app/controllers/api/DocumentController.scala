package controllers.api

import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DbDocumentBackend,DocumentBackend}

trait DocumentController extends ApiController {
  protected val backend: DocumentBackend

  def index(documentSetId: Long, q: String) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    backend.index(documentSetId, q).map { documents =>
      Ok(views.json.api.Document.index(documents))
    }
  }
}

object DocumentController extends DocumentController {
  override protected val backend = DocumentBackend
}
