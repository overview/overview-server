package org.overviewproject.database.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.DocumentSearchResult
import org.overviewproject.database.orm.Schema

object DocumentSearchResultFinder extends Finder with FindableByDocumentSet[DocumentSearchResult] {

  override def byDocumentSet(documentSetId: Long): FinderResult[DocumentSearchResult] = {
    val searchResultIds = from(Schema.searchResults)(s =>
      where (s.documentSetId === documentSetId)
      select (s.id))
      
    from(Schema.documentSearchResults)(ds => 
      where (ds.searchResultId in searchResultIds)
      select (ds))
  }
    
}