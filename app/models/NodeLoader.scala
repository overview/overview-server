package models

import org.overviewproject.tree.orm.Node
import org.overviewproject.postgres.CustomTypes._
import models.core.DocumentIdList

class NodeLoader {

  import models.orm.Schema._
  
  def loadRoot(documentSetId: Long): Option[core.Node] = {
    nodes.where(n => n.documentSetId === documentSetId and n.parentId === Option.empty[Long]).headOption.map { n =>
      core.Node(n.id, n.description, Seq.empty, DocumentIdList(n.cachedDocumentIds, n.cachedSize), Map())
    }
  }
  
  def loadNode(documentSetId: Long, nodeId: Long): Option[core.Node] = {
    nodes.where(n => n.documentSetId === documentSetId and n.id === nodeId).headOption.map { n =>
      core.Node(n.id, n.description, Seq.empty, DocumentIdList(n.cachedDocumentIds, n.cachedSize), Map())
    }
  }
  
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