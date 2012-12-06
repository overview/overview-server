package models

import org.overviewproject.tree.orm.Node
import org.overviewproject.postgres.CustomTypes._

class NodeLoader {

  import models.orm.Schema._
  
  def loadRoot(documentSetId: Long): Option[Node] = {
    nodes.where(n => n.documentSetId === documentSetId and n.parentId === Option.empty[Long]).headOption
  }
  
  def loadTree(documentSetId: Long, root: Node, depth: Int): Seq[Node] = {
    if (depth == 0) Seq(root)
    else {
      val children = nodes.where(n => n.documentSetId === documentSetId and n.parentId === root.id).iterator
      if (children.isEmpty) Seq(root)
      else root +: children.flatMap(c => loadTree(documentSetId, c, depth - 1)).toSeq
    }
  }
}