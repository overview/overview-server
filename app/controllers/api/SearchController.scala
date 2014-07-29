package controllers.api

import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.SearchBackend

trait SearchController extends ApiController {
  val backend: SearchBackend

  def index(id: Long) = ApiAuthorizedAction(userOwningDocumentSet(id)).async {
    for (searchResults <- backend.findSearchResults(id))
      yield Ok(views.json.api.SearchResult.index(searchResults))
  }
}

object SearchController extends SearchController {
  override val backend = SearchBackend
}
