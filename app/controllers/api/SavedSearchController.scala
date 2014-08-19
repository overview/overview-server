package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.SavedSearchBackend
import controllers.forms.SearchForm
import org.overviewproject.tree.orm.SearchResult // FIXME should be models.SearchResult

trait SavedSearchController extends ApiController {
  val backend: SavedSearchBackend

  def index(id: Long) = ApiAuthorizedAction(userOwningDocumentSet(id)).async {
    for (searchResults <- backend.index(id))
      yield Ok(views.json.api.SearchResult.index(searchResults))
  }

  def show(id: Long, query: String) = ApiAuthorizedAction(userOwningDocumentSet(id)).async {
    backend.show(id, query).map(_ match {
      case None => NotFound
      case Some(searchResult) => Ok(views.json.api.SearchResult.show(searchResult))
    })
  }

  def create(id: Long) = ApiAuthorizedAction(userOwningDocumentSet(id)).async { request =>
    SearchForm(id).bindFromRequest()(request).value match {
      case None => Future.successful(BadRequest(jsonError("""You must POST an Object like { "query": "foo" }""")))
      case Some(search) => {
        backend.create(search).map(Unit => NoContent)
      }
    }
  }

  def destroy(id: Long, query: String) = ApiAuthorizedAction(userOwningDocumentSet(id)).async {
    backend.destroy(id, query).map(Unit => NoContent)
  }
}

object SavedSearchController extends SavedSearchController {
  override val backend = SavedSearchBackend
}
