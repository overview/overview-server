package org.overviewproject.database.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.database.orm.Schema._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.Page

object PageFinder extends Finder {
  type PageFinderResult = FinderResult[Page]
  
  def byDocumentSet(documentSetId: Long): PageFinderResult = 
    from(tempDocumentSetFiles, pages)((dsf, p) =>
      where (dsf.documentSetId === documentSetId and p.fileId === dsf.fileId)
      select (p))
      
}