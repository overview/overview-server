package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema.{nodeDocuments, documents }
import org.overviewproject.tree.orm.NodeDocument
import org.overviewproject.tree.orm.finders.{ BaseNodeDocumentFinder, FinderResult }


object NodeDocumentFinder extends BaseNodeDocumentFinder(nodeDocuments, documents) with FindableByDocumentSet[NodeDocument] {
  type NodeDocumentFinderResult = FinderResult[NodeDocument]
  
  def byDocumentSet(documentSetId: Long): NodeDocumentFinderResult = byDocumentSetQuery(documentSetId)
}