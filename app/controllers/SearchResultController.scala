package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.forms.NodeIdsForm
import org.overviewproject.tree.orm.SearchResult
import models.orm.finders.{NodeDocumentFinder,SearchResultFinder}

trait SearchResultController extends Controller {
  trait Storage {
    def findSearchResults(documentSetId: Long) : Iterable[SearchResult]

    /** @return a SearchResult if it exists in the given DocumentSet. */
    def findSearchResult(documentSetId: Long, searchResultId: Long) : Option[SearchResult]

    /** Returns an Iterable of (nodeId, count) pairs.
      *
      * Security considerations: for speed, we do not verify that the user has
      * access to the given nodeIds. Therefore this method should return every
      * nodeId provided--as 0 if there are no node+search connections. Beware:
      * the nodeIds should be returned in the order they are provided, so users
      * can't glean any information about their existence from their ordering.
      *
      * The caller must verify that the user has access to the given
      * searchResultId. That is enough to ensure the user can't gain information
      * about other users' nodes.
      *
      * @return Iterable of (nodeId, count) pairs
      */
    def searchResultCountsByNodeId(searchResultId: Long, nodeIds: Iterable[Long]) : Iterable[(Long,Int)]
  }

  val storage : SearchResultController.Storage

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val searchResults = storage.findSearchResults(documentSetId)

    Ok(views.json.SearchResult.index(searchResults))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  def nodeCounts(documentSetId: Long, searchResultId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    NodeIdsForm().bindFromRequest.fold(
      formWithErrors => BadRequest,
      nodeIds => {
        storage.findSearchResult(documentSetId, searchResultId) match {
          case None => NotFound
          case Some(searchResult) => {
            val counts = storage.searchResultCountsByNodeId(searchResultId, nodeIds)
            Ok(views.json.helper.nodeCounts(counts))
          }
        }
      }
    )
  }
}

object SearchResultController extends SearchResultController {
  override val storage = new Storage {
    override def findSearchResults(documentSetId: Long) = {
      SearchResultFinder.byDocumentSet(documentSetId).map(_.copy()) // copy() to avoid "This ResultSet is closed" error
    }

    override def findSearchResult(documentSetId: Long, searchResultId: Long) = {
      SearchResultFinder.byDocumentSetAndId(documentSetId, searchResultId).headOption
    }

    override def searchResultCountsByNodeId(searchResultId: Long, nodeIds: Iterable[Long]) = {
      val counts = NodeDocumentFinder
        .byNodeIds(nodeIds)
        .searchResultCountsByNodeId(searchResultId)
        .toMap
      nodeIds.map(nodeId => (nodeId -> counts.getOrElse(nodeId, 0L).toInt))
    }
  }
}
