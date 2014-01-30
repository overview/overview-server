package org.overviewproject.tree.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.squeryl.Table
import org.overviewproject.tree.orm.{ Node, Tree }

class BaseNodeStore(nodes: Table[Node], trees: Table[Tree]) extends BaseStore(nodes) {
  
  def deleteByDocumentSet(documentSetId: Long): Int = {
    nodes.deleteWhere { n =>
      n.treeId in from(trees)(t =>
        where(t.documentSetId === documentSetId)
          select (t.id))
    }
  }
}


object BaseNodeStore {
  def apply(nodes: Table[Node], trees: Table[Tree]) = new BaseNodeStore(nodes, trees)
}