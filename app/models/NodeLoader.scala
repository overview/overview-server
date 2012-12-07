package models

import org.overviewproject.tree.orm.Node
import org.overviewproject.postgres.CustomTypes._
import models.core.DocumentIdList

class NodeLoader {

  import models.orm.Schema._

  def loadRoot(documentSetId: Long): Option[core.Node] = {
    nodes.where(n => n.documentSetId === documentSetId and n.parentId === Option.empty[Long]).headOption.map(makePartialNode)
  }

  def loadRootId(documentSetId: Long): Option[Long] = {
    from(nodes)(n => where(n.documentSetId === documentSetId and n.parentId === Option.empty[Long]) select (n.id)).headOption
  }

  def loadNode(documentSetId: Long, nodeId: Long): Option[core.Node] = {
    nodes.where(n => n.documentSetId === documentSetId and n.id === nodeId).headOption.map(makePartialNode)
  }

  def loadTree(documentSetId: Long, rootId: Long, depth: Int): Seq[core.Node] = {
    loadNode(documentSetId, rootId).map { root => 
      loadLevels(documentSetId, Seq(root), depth + 1)
    }.getOrElse(Seq.empty)
  }

  private def loadLevels(documentSetId: Long, baseNodes: Seq[core.Node], depth: Int): Seq[core.Node] = {
    if (depth == 0) Seq.empty
    else {
      val baseIds = baseNodes.map(_.id)
      val nextLevel = loadChildren(documentSetId, baseIds) //nodes.where(n => n.documentSetId === documentSetId and (n.parentId in baseIds)).iterator.toSeq
      val baseWithChildIds = baseNodes.map { n => 
        val childIds = nextLevel.filter(_.parentId == Some(n.id)).map(_.id)
        n.copy(childNodeIds = childIds)  
      }
      baseWithChildIds ++ loadLevels(documentSetId, nextLevel.map(makePartialNode), depth - 1)
    }
  }
  
  private def loadChildren(documentSetId: Long, nodeIds: Seq[Long]): Seq[Node] = {
    nodes.where(n => n.documentSetId === documentSetId and (n.parentId in nodeIds)).iterator.toSeq
  }
  
  private def makePartialNode(n: Node): core.Node = 
    core.Node(n.id, n.description, Seq.empty, DocumentIdList(n.cachedDocumentIds, n.cachedSize), Map()) 


  def loadTree(documentSetId: Long, root: core.Node, depth: Int): Seq[core.Node] = {
    if (depth == 0) Seq(root)
    else {
      val children = nodes.where(n => n.documentSetId === documentSetId and n.parentId === root.id).iterator.toSeq
      if (children.isEmpty) Seq(root)
      else {
        val childIds = children.map(_.id)
        val childNodes = children.map(c => core.Node(c.id, c.description, Seq.empty, DocumentIdList(c.cachedDocumentIds, c.cachedSize), Map()))
        root.copy(childNodeIds = childIds) +: childNodes.flatMap(c => loadTree(documentSetId, c, depth - 1)).toSeq
      }
    }
  }

}