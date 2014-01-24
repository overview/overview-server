package org.overviewproject.persistence.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.persistence.orm.Schema.{ nodeDocuments, nodes }

object NodeDocumentStore extends BaseStore(nodeDocuments) {

  def deleteByDocumentSetId(documentSetId: Long): Int = {
    val nodesInDocumentSet = from(nodes)(n =>
      where(n.documentSetId === documentSetId)
        select (n.id))
    nodeDocuments.deleteWhere(nd =>
      (nd.nodeId in nodesInDocumentSet))
  }
}