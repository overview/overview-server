package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.SearchResult
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder

trait SearchResultFinder extends DocumentSetComponentFinder[SearchResult] {
  type SearchResultFinderResult = FinderResult[SearchResult]

  def byDocumentSetAndQuery(documentSet: Long, query: String): SearchResultFinderResult =
    byDocumentSet(documentSet).where(_.query === query)

}

object SearchResultFinder extends Finder {
  def apply() =  new SearchResultFinder {
    override val table = Schema.searchResults
  }

}

