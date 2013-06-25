package controllers

import play.api.mvc.Controller
import scala.annotation.tailrec

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import org.overviewproject.tree.orm.{Node,SearchResult,Tag}
import models.orm.finders.{NodeFinder,SearchResultFinder,TagFinder}
import models.orm.stores.NodeStore

trait NodeController extends Controller {
  private[controllers] val rootChildLevels = 2 // When showing the root, show this many levels of children

  trait Storage {
    def findRootNodesWithChildIds(documentSetId: Long, depth: Int) : Iterable[(Node,Iterable[Long])]
    def findChildNodesWithChildIds(documentSetId: Long, parentNodeId: Long) : Iterable[(Node,Iterable[Long])]
    def findNode(documentSetId: Long, nodeId: Long) : Iterable[Node]
    def findTags(documentSetId: Long) : Iterable[Tag]
    def findSearchResults(documentSetId: Long) : Iterable[SearchResult]

    def updateNode(node: Node) : Node
  }
  val storage : NodeController.Storage

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val nodes = storage.findRootNodesWithChildIds(documentSetId, rootChildLevels)

    if (nodes.isEmpty) {
      NotFound
    } else {
      val tags = storage.findTags(documentSetId)
      val searchResults = storage.findSearchResults(documentSetId)
      Ok(views.json.Tree.show(nodes, tags, searchResults))
        .withHeaders(CACHE_CONTROL -> "max-age=0")
    }
  }

  def show(documentSetId: Long, id: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val nodes = storage.findChildNodesWithChildIds(documentSetId, id)

    Ok(views.json.Tree.show(nodes))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  def update(documentSetId: Long, id: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    storage.findNode(documentSetId, id).headOption match {
      case None => NotFound
      case Some(node) =>
        val form = forms.NodeForm(node)

        form.bindFromRequest().fold(
          f => BadRequest,
          node => {
            val savedNode = storage.updateNode(node)
            Ok(views.json.Node.show(savedNode))
          })
    }
  }
}

object NodeController extends NodeController {
  override val storage = new NodeController.Storage {
    private def addChildIds(nodes: Iterable[Node]) : Iterable[(Node,Iterable[Long])] = {
      if (nodes.nonEmpty) {
        val nodeIds = nodes.map(_.id)
        val nodeChildIds : Map[Option[Long],Iterable[Long]] = NodeFinder
          .byParentIds(nodeIds)
          .toParentIdAndId
          .groupBy(_._1)
          .mapValues((x: Iterable[(Option[Long],Long)]) => x.map(_._2))

        nodes.map(node => (node, nodeChildIds.getOrElse(Some(node.id), Seq())))
      } else {
        Seq()
      }
    }

    private def childrenOf(nodes: Iterable[Node]) : Iterable[Node] = {
      if (nodes.nonEmpty) {
        val nodeIds = nodes.map(_.id)
        NodeFinder
          .byParentIds(nodeIds)
          .map(_.copy()) // work around Squeryl bug
      } else {
        Seq()
      }
    }

    @tailrec
    private def addChildNodes(parentNodes: Iterable[Node], thisLevelNodes: Iterable[Node], depth: Int) : Iterable[Node] = {
      if (thisLevelNodes.isEmpty) {
        parentNodes
      } else if (depth == 0) {
        parentNodes ++ thisLevelNodes
      } else {
        addChildNodes(parentNodes ++ thisLevelNodes, childrenOf(thisLevelNodes), depth - 1)
      }
    }

    override def findRootNodesWithChildIds(documentSetId: Long, depth: Int) = {
      val root : Iterable[Node] = NodeFinder.byDocumentSetAndParent(documentSetId, None)
        .map(_.copy()) // Squeryl bug
      val nodes = addChildNodes(Seq(), root, depth)
      addChildIds(nodes)
    }

    override def findChildNodesWithChildIds(documentSetId: Long, parentNodeId: Long) = {
      val nodes = NodeFinder.byDocumentSetAndParent(documentSetId, Some(parentNodeId))
        .map(_.copy()) // Squeryl bug
      addChildIds(nodes)
    }

    override def findTags(documentSetId: Long) = {
      TagFinder.byDocumentSet(documentSetId)
    }

    override def findSearchResults(documentSetId: Long) = {
      SearchResultFinder.byDocumentSet(documentSetId)
    }

    override def findNode(documentSetId: Long, nodeId: Long) = {
      NodeFinder.byDocumentSetAndId(documentSetId, nodeId)
    }

    override def updateNode(node: Node) = {
      NodeStore.update(node)
    }
  }
}
