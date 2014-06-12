package org.overviewproject.persistence.orm.finders

import scala.language.implicitConversions
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.Page
import org.overviewproject.persistence.orm.Schema.{ pages, tempDocumentSetFiles }
import org.squeryl.Query

object PageFinder extends Finder {

  class PageFinderResult(query: Query[Page]) extends FinderResult(query) {
    def withoutData: FinderResult[(Long, Int, Option[String], Option[String])] = 
      from(query)(p =>
        select ((p.id, p.pageNumber, p.text, p.textErrorMessage))  
      )
  }
  implicit private def queryToPageFinderResult(query: Query[Page]): PageFinderResult = new PageFinderResult(query)
  
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