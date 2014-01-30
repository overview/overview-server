package org.overviewproject.persistence.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.persistence.orm.Schema.{ nodes, trees }

object NodeStore extends BaseStore(nodes) {
  
  def deleteByDocumentSetId(documentSetId: Long): Int = {
    nodes.deleteWhere { n =>
      n.treeId in from(trees)(t =>
        where(t.documentSetId === documentSetId)
          select (t.id))
    }
  }

}