package controllers

import java.sql.Connection
import play.api.mvc.Controller
import play.api.libs.json.JsValue

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import org.overviewproject.tree.orm.{Document,DocumentSet,Node,SearchResult}
import models.{IdList,SubTreeLoader}
import models.orm.{Tag}
import models.orm.finders.{DocumentFinder,NodeFinder,SearchResultFinder,TagFinder}
import models.orm.stores.NodeStore

trait NodeController extends Controller {
  private val childLevels = 2 // When showing the root, show this many levels of children

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
    val subTreeLoader = new SubTreeLoader(documentSetId)

    subTreeLoader.loadRootId match {
      case Some(rootId) => {
        val nodes = subTreeLoader.load(rootId, childLevels)
        val documentIds = nodes.flatMap(_.documentIds.firstIds).distinct
        val documents : Iterable[(Document,Seq[Long],Seq[Long])] = DocumentFinder.byIds(documentIds)
          .withNodeIdsAndTagIdsAsLongStrings
          .map((tuple: (Document, Option[String], Option[String])) =>
            // copy() is because Squeryl frees Strings too early
            (tuple._1.copy(), IdList.longs(tuple._2.getOrElse("")).ids, IdList.longs(tuple._3.getOrElse("")).ids))
        val tags : Iterable[(Tag,Long)] = TagFinder.byDocumentSet(documentSetId).withCounts
        val searchResults : Iterable[SearchResult] = SearchResultFinder.byDocumentSet(documentSetId)
        val json = views.json.Tree.show(nodes, documents, tags, searchResults)

        Ok(json)
      }
      case None => NotFound
    }
  }

  def show(documentSetId: Long, id: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
    val subTreeLoader = new SubTreeLoader(documentSetId)

    val nodes = subTreeLoader.load(id, 1)
    val documentIds = nodes.flatMap(_.documentIds.firstIds).distinct
    val documents : Iterable[(Document,Seq[Long],Seq[Long])] = DocumentFinder.byIds(documentIds)
      .withNodeIdsAndTagIdsAsLongStrings
      .map((tuple: (Document, Option[String], Option[String])) =>
        // copy() is because Squeryl frees Strings too early
        (tuple._1.copy(), IdList.longs(tuple._2.getOrElse("")).ids, IdList.longs(tuple._3.getOrElse("")).ids))

    val json = views.json.Tree.show(nodes, documents, Seq(), Seq())
    Ok(json)
  }

  def update(documentSetId: Long, id: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    findNode(documentSetId, id) match {
      case None => NotFound
      case Some(node) =>
        val form = forms.NodeForm(node)

        form.bindFromRequest().fold(
          f => BadRequest,
          node => {
            val savedNode = updateNode(node)
            Ok(views.json.Node.show(savedNode))
          })
    }
  }

  protected def findNode(documentSetId: Long, id: Long) : Option[Node]
  protected def updateNode(node: Node) : Node
}

object NodeController extends NodeController {
  override protected def findNode(documentSetId: Long, id: Long) = {
    NodeFinder.byDocumentSetAndId(documentSetId, id).headOption
  }

  override protected def updateNode(node: Node) = {
    NodeStore.update(node)
  }
}
