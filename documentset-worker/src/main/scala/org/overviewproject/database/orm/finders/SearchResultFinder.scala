package org.overviewproject.database.orm.finders


import org.overviewproject.database.orm.{ SearchResult, Schema }
import org.overviewproject.postgres.SquerylEntrypoint._

object SearchResultFinder extends Finder {

  type SearchResultFinderResult = FinderResult[SearchResult]
  
  def byDocumentSet(documentSet: Long): SearchResultFinderResult =
    Schema.searchResults.where(_.documentSetId === documentSet)

  def byDocumentSetAndQuery(documentSet: Long, query: String): SearchResultFinderResult =
    byDocumentSet(documentSet).where(_.query === query)

}

