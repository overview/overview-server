package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.Page
import org.overviewproject.persistence.orm.Schema.{ pages, tempDocumentSetFiles }

object PageFinder extends Finder {

  type PageFinderResult = FinderResult[Page]

  def byFileId(fileId: Long): PageFinderResult = {
    from(pages)(p =>
      where(p.fileId === fileId)
        select (p)
        orderBy (p.pageNumber))
  }

  def byDocumentSet(documentSetId: Long): PageFinderResult = {
    from(tempDocumentSetFiles, pages)((tdf, p) =>
      where (tdf.documentSetId === documentSetId and tdf.fileId === p.fileId)
      select (p)
    )
  }
}