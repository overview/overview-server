package org.overviewproject.database.orm.finders


import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.SearchResult
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

object SearchResultFinder extends Finder {

  type SearchResultFinderResult = FinderResult[SearchResult]
  
  def byDocumentSet(documentSet: Long): SearchResultFinderResult =
    Schema.searchResults.where(_.documentSetId === documentSet)

  def byDocumentSetAndQuery(documentSet: Long, query: String): SearchResultFinderResult =
    byDocumentSet(documentSet).where(_.query === query)

}

