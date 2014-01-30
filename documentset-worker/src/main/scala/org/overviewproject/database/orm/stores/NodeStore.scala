package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema.{ nodes, trees }

object NodeStore extends BaseStore(nodes) {

  def deleteByDocumentSet(documentSetId: Long): Int = {
    nodes.deleteWhere { n =>
      n.treeId in from(trees)(t =>
        where(t.documentSetId === documentSetId)
          select (t.id))
    }
  }
}