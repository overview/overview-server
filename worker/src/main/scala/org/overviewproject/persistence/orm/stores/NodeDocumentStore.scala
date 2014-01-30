package org.overviewproject.persistence.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.persistence.orm.Schema.{ nodeDocuments, nodes, trees }

object NodeDocumentStore extends BaseStore(nodeDocuments) {

  def deleteByDocumentSet(documentSetId: Long): Int = {
    val treesInDocumentSet = from(trees)(t => 
      where(t.documentSetId === documentSetId)
      select (t.id))
      
    val nodesInTrees = from(nodes)(n =>
      where(n.treeId in treesInDocumentSet)
        select (n.id))
        
    nodeDocuments.deleteWhere(nd =>
      (nd.nodeId in nodesInTrees))
  }
}