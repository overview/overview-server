package org.overviewproject.database.orm.finders

import org.overviewproject.tree.orm.finders.FinderResult

trait FindableByDocumentSet[A] {
  
  def byDocumentSet(documentSetId: Long): FinderResult[A]
}