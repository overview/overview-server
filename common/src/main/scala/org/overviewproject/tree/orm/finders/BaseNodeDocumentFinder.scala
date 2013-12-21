package org.overviewproject.tree.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.{ Document, NodeDocument }
import org.squeryl.{ Query, Table }

class BaseNodeDocumentFinder(table: Table[NodeDocument], documentsTable: Table[Document]) extends 
DocumentSetRelationFinder(table, documentsTable) {

  def byDocumentSetQuery(documentSetId: Long): Query[NodeDocument] =
    relationByDocumentSetComponent(d => d.documentSetId === documentSetId, d => d.id, nd => nd.documentId)
}