package org.overviewproject.persistence.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.orm.Schema.{ nodeDocuments, nodes }

object NodeDocumentStore {
  def deleteByRoot(rootId: Long): Int = {
    val nodesInTrees = from(nodes)(n =>
      where(n.rootId === rootId)
      select(n.id)
    )

    nodeDocuments.deleteWhere(nd => (nd.nodeId in nodesInTrees))
  }
}
