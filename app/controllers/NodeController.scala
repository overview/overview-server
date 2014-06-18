package controllers

import play.api.mvc.Controller
import scala.annotation.tailrec

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningTree
import org.overviewproject.tree.orm.{Node,Tree}
import models.orm.finders.{NodeFinder,TreeFinder}
import models.orm.stores.NodeStore

trait NodeController extends Controller {
  private[controllers] val rootChildLevels = 2 // When showing the root, show this many levels of children

  trait Storage {
    /** A tree of Nodes for the document set, starting at the root.
      *
      * Nodes are returned in order: when iterating over the return value, if a
      * Node refers to a parentId, the Node corresponding to that parentId has
      * already appeared in the return value.
      */
    def findRootNodes(treeId: Long, depth: Int) : Iterable[Node]

    /** The direct descendents of the given parent Node ID. */
    def findChildNodes(documentSetId: Long, parentNodeId: Long) : Iterable[Node]

    def findTree(treeId: Long) : Option[Tree]
    def findNode(treeId: Long, nodeId: Long) : Iterable[Node]

    def updateNode(node: Node) : Node
  }
  val storage : NodeController.Storage

  def index(treeId: Long) = AuthorizedAction(userOwningTree(treeId)) { implicit request =>
    storage.findTree(treeId) match {
      case None => NotFound
      case Some(tree) => {
        val nodes = storage.findRootNodes(treeId, rootChildLevels)

        if (nodes.isEmpty) {
          NotFound
        } else {
          Ok(views.json.Node.index(nodes))
            .withHeaders(CACHE_CONTROL -> "max-age=0")
        }
      }
    }
  }

  def show(treeId: Long, id: Long) = AuthorizedAction(userOwningTree(treeId)) { implicit request =>
    val nodes = storage.findChildNodes(treeId, id)

    Ok(views.json.Node.index(nodes))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  def update(treeId: Long, id: Long) = AuthorizedAction(userOwningTree(treeId)) { implicit request =>
    storage.findNode(treeId, id).headOption match {
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

    override def findRootNodes(treeId: Long, depth: Int) = {
      val root : Iterable[Node] = NodeFinder.byTreeAndParent(treeId, None)
        .map(_.copy()) // Squeryl bug
      addChildNodes(Seq(), root, depth)
    }

    override def findChildNodes(treeId: Long, parentNodeId: Long) = {
      NodeFinder.byTreeAndParent(treeId, Some(parentNodeId))
        .map(_.copy()) // Squeryl bug
    }

    override def findTree(treeId: Long) = {
      TreeFinder.byId(treeId).headOption
    }

    override def findNode(treeId: Long, nodeId: Long) = {
      NodeFinder.byTreeAndId(treeId, nodeId)
    }

    override def updateNode(node: Node) = {
      NodeStore.update(node)
    }
  }
}
