package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema.{documentTags, tags}
import org.overviewproject.tree.orm.finders.{ BaseDocumentTagFinder, FinderResult }
import org.overviewproject.tree.orm.DocumentTag

object DocumentTagFinder extends BaseDocumentTagFinder(documentTags, tags) {

  type DocumentTagFinderResult = FinderResult[DocumentTag]
  
  def byDocumentSet(documentSetId: Long): DocumentTagFinderResult = byDocumentSetQuery(documentSetId)
}