package controllers

import play.api.mvc.Controller
import play.api.libs.json.JsValue
import scala.collection.mutable.Buffer

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import org.overviewproject.tree.orm.{Document,DocumentSet,Node,SearchResult}
import models.IdList
import models.orm.{Tag}
import models.orm.finders.{DocumentFinder,NodeFinder,NodeDocumentFinder,SearchResultFinder,TagFinder}
import models.orm.stores.NodeStore

trait NodeController extends Controller {
  private[controllers] val rootChildLevels = 2 // When showing the root, show this many levels of children

  private def nodesToJson(documentSetId: Long, nodes: Iterable[Node]) = {
    // FIXME move this to "models" and test it

    val nodeIds = nodes.map(_.id)

    val nodeTagCounts : Map[Long,Iterable[(Long,Long)]] = NodeDocumentFinder
      .byNodeIds(nodeIds)
      .allTagCountsByNodeId
      .groupBy(_._1)
      .mapValues((x: Iterable[(Long,Long,Long)]) => x.map({ y: (Long,Long,Long) => (y._2, y._3) }))

    val nodeSearchResultCounts : Map[Long,Iterable[(Long,Long)]] = NodeDocumentFinder
      .byNodeIds(nodeIds)
      .allSearchResultCountsByNodeId
      .groupBy(_._1)
      .mapValues((x: Iterable[(Long,Long,Long)]) => x.map({ y: (Long,Long,Long) => (y._2, y._3) }))

    val nodeChildIds : Map[Option[Long],Iterable[Long]] = NodeFinder
      .byParentIds(nodeIds)
      .toParentIdAndId
      .groupBy(_._1)
      .mapValues((x: Iterable[(Option[Long],Long)]) => x.map(_._2))

    val nodesWithChildIdsAndCounts : Iterable[(Node,Iterable[Long],Iterable[(Long,Long)],Iterable[(Long,Long)])] = nodes
      .map({ node: Node => (
        node,
        nodeChildIds.getOrElse(Some(node.id), Seq()),
        nodeTagCounts.getOrElse(node.id, Seq()),
        nodeSearchResultCounts.getOrElse(node.id, Seq())
      )})

    val documentIds = nodes.flatMap(_.cachedDocumentIds).toSeq.distinct

    val documents : Iterable[(Document,Seq[Long],Seq[Long])] = DocumentFinder.byIds(documentIds)
      .withNodeIdsAndTagIdsAsLongStrings
      .map((tuple: (Document, Option[String], Option[String])) =>
        // copy() is because Squeryl frees Strings too early
        (tuple._1.copy(), IdList.longs(tuple._2.getOrElse("")).ids, IdList.longs(tuple._3.getOrElse("")).ids))
    val tags : Iterable[(Tag,Long)] = TagFinder.byDocumentSet(documentSetId).withCounts
    val searchResults : Iterable[SearchResult] = SearchResultFinder.byDocumentSet(documentSetId)

    views.json.Tree.show(nodesWithChildIdsAndCounts, documents, tags, searchResults)
  }

  private def showNodesStartingAt(documentSetId: Long, node: Node, childLevels: Int) = {
    // FIXME move this to "models" and test it

    var nodes : Buffer[Node] = Buffer(node)

    var thisLevel : Iterable[Node] = nodes.toSeq
    for (i <- 0 to childLevels - 1) {
      val nodeIds = thisLevel.map(_.id)
      if (nodeIds.nonEmpty) {
        thisLevel = NodeFinder
          .byParentIds(nodeIds)
          .map(_.copy())

        nodes ++= thisLevel
      }
    }

    nodesToJson(documentSetId, nodes.toSeq)
  }

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val root : Iterable[Node] = NodeFinder
      .byDocumentSetAndParent(documentSetId, None)
      .map(_.copy()) // avoid Squeryl bug

    if (root.isEmpty) {
      NotFound
    } else {
      Ok(showNodesStartingAt(documentSetId, root.head, rootChildLevels))
    }
  }

  def show(documentSetId: Long, id: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val root : Iterable[Node] = NodeFinder
      .byDocumentSetAndId(documentSetId, id)
      .map(_.copy()) // avoid Squeryl bug

    if (root.isEmpty) {
      NotFound
    } else {
      Ok(showNodesStartingAt(documentSetId, root.head, 1))
    }
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
