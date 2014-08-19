package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{SavedSearchBackend, SavedSearchDocumentBackend}

trait SavedSearchDocumentController extends ApiController {
  val searchBackend: SavedSearchBackend
  val backend: SavedSearchDocumentBackend

  def index(id: Long, query: String) = ApiAuthorizedAction(userOwningDocumentSet(id)).async {
    searchBackend.show(id, query).flatMap(_ match {
      case None => Future(NotFound(jsonError("""Search not found. You need to POST to /api/v1/document-sets/:id/search before you can GET the list of documents for that search.""")))
      case Some(searchResult) =>
        for (documents <- backend.index(searchResult.id))
          yield Ok(views.json.api.Document.index(documents))
    })
  }
}

object SavedSearchDocumentController extends SavedSearchDocumentController {
  override val searchBackend = SavedSearchBackend
  override val backend = SavedSearchDocumentBackend
}
