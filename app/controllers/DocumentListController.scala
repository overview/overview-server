package controllers

import play.api.mvc.{AnyContent, Controller, Request}

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import models.{ SelectionRequest,IdList }
import models.orm.finders.DocumentFinder
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.ResultPage

trait DocumentListController extends Controller {
  val MaxPageSize = 100

  trait Storage {
    def findDocuments(selection: SelectionRequest, pageSize: Int, page: Int) : ResultPage[(Document,Seq[Long],Seq[Long])]
  }

  val storage : DocumentListController.Storage

  def index(documentSetId: Long, nodes: String, tags: String,
            documents: String, searchResults: String, pageSize: Int, page: Int)
            = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>

    val realPageSize = math.max(0, math.min(pageSize, MaxPageSize))
    val realPage = math.max(1, page)

    val selection = SelectionRequest(documentSetId, nodes, tags, documents, searchResults)
    val documentPage = storage.findDocuments(selection, realPageSize, realPage)

    Ok(views.json.DocumentList.show(documentPage))
  }
}

object DocumentListController extends DocumentListController {
  override val storage = new Storage {
    def findDocuments(selection: SelectionRequest, pageSize: Int, page: Int) = {
      val ids = DocumentFinder.bySelectionRequest(selection).toIdsOrdered
      val idsToResults = { (ids: Traversable[Long]) =>
        DocumentFinder.byIds(ids).withNodeIdsAndTagIdsAsLongStrings.toQuery
      }

      ResultPage(ids, idsToResults, pageSize, page).map(((d: Document, nodeIds: Option[String], tagIds: Option[String]) =>
        (
          d,
          IdList.longs(nodeIds.getOrElse("")).ids,
          IdList.longs(tagIds.getOrElse("")).ids
        )
      ).tupled)
    }
  }
}
