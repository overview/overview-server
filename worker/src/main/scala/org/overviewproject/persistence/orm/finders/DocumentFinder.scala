package org.overviewproject.persistence.orm.finders

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

object DocumentFinder extends Finder {

  type DocumentFinderResult = FinderResult[Document]
  
  def byDocumentSet(documentSet: Long): DocumentFinderResult = {
    Schema.documents.where(d => d.documentSetId === documentSet)
  }

}