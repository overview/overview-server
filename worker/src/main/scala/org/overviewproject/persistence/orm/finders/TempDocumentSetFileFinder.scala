package org.overviewproject.persistence.orm.finders

import scala.language.implicitConversions
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.tree.orm.TempDocumentSetFile
import org.overviewproject.tree.orm.finders.Finder
import org.squeryl.Query
import org.overviewproject.tree.orm.finders.FinderResult
import org.overviewproject.persistence.orm.Schema.tempDocumentSetFiles

object TempDocumentSetFileFinder extends Finder {

  class TempDocumentSetFileFinderResult(query: Query[TempDocumentSetFile]) extends FinderResult(query) {
    
    def orderByFileIds: TempDocumentSetFileFinderResult = {
      from(query)(t =>
        select (t)
        orderBy (t.fileId)
      )
    }
  }
  
  implicit private def queryToTempDocumentSetFileFinderResult(query: Query[TempDocumentSetFile]): TempDocumentSetFileFinderResult = 
    new TempDocumentSetFileFinderResult(query)
  
  def byDocumentSet(documentSetId: Long): TempDocumentSetFileFinderResult = {
    from(tempDocumentSetFiles)(t =>
      where (t.documentSetId === documentSetId)
      select (t)
    )
  }
}