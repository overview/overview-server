package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.SearchResult
import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._

object SearchResultFinder extends Finder {
  
  type SearchResultResult = FinderResult[SearchResult]
  
  def byDocumentSet(documentSet: Long): SearchResultResult =
    Schema.searchResults.where(_.id === documentSet)
    
  def byDocumentSetAndQuery(documentSet: Long, query: String): SearchResultResult =
    byDocumentSet(documentSet).where(_.query === query)

}