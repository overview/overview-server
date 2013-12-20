package org.overviewproject.tree.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.{ Document, NodeDocument }
import org.squeryl.{ Query, Table }

class BaseNodeDocumentFinder(table: Table[NodeDocument], documentsTable: Table[Document]) extends  Finder {

  def byDocumentSetQuery(documentSetId: Long): Query[NodeDocument] = {
    // join through documents, not nodes: there are fewer documents than nodes
    // Select as WHERE with a subquery, to circumvent Squeryl delete() missing the join
    val documentIds = from(documentsTable)(d =>
      where(d.documentSetId === documentSetId)
        select (d.id))

    from(table)(nd =>
      where(nd.documentId in documentIds)
        select (nd))
    
  }

}