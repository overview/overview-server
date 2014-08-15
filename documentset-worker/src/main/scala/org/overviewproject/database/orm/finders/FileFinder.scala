package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema._
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.File

object FileFinder extends Finder {
  type FileFinderResult = FinderResult[File]
  
  def byDocumentSet(documentSetId: Long): FileFinderResult = 
    from(tempDocumentSetFiles, files)((dsf, f) =>
      where(dsf.documentSetId === documentSetId and dsf.fileId === f.id)
      select (f))
    
  def byDocumentSetPaged(documentSetId: Long, page: Int, pageSize: Int): FileFinderResult = {
    val offset = page * pageSize
    from(tempDocumentSetFiles, files)((dsf, f) =>
      where(dsf.documentSetId === documentSetId and dsf.fileId === f.id)
      select(f)
      orderBy(f.id)).page(offset, pageSize)
  }

}
