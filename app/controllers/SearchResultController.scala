package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import org.overviewproject.tree.orm.SearchResult
import models.orm.finders.SearchResultFinder

trait SearchResultController extends Controller {
  trait Storage {
    def findSearchResults(documentSetId: Long) : Iterable[SearchResult]
  }

  val storage : SearchResultController.Storage

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val searchResults = storage.findSearchResults(documentSetId)
    Ok(views.json.SearchResult.index(searchResults))
  }
}

object SearchResultController extends SearchResultController {
  override val storage = new Storage {
    def findSearchResults(documentSetId: Long) = {
      SearchResultFinder.byDocumentSet(documentSetId).toSeq
    }
  }
}
