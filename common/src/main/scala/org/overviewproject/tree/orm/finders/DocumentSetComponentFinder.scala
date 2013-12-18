package org.overviewproject.tree.orm.finders

import org.overviewproject.tree.orm.DocumentSetComponent
import org.squeryl.Table
import org.overviewproject.postgres.SquerylEntrypoint._

trait DocumentSetComponentFinder[A] extends Finder {
  def byDocumentSet(documentSetId: Long): FinderResult[A]   
}

object DocumentSetComponentFinder {
  def apply[A <: DocumentSetComponent](table: Table[A]) = new DocumentSetComponentFinder[A] {
    
    def byDocumentSet(documentSetId: Long): FinderResult[A] = 
      from(table)(c =>
        where(c.documentSetId === documentSetId)
        select(c)
      )
  } 
    
    
}