package models.orm.finders

import org.squeryl.Query

import models.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.SearchResult
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

object SearchResultFinder extends Finder {
  class SearchResultResult(query: Query[SearchResult]) extends FinderResult(query) {
    /** Orders and filters the result, so you only get 20 SearchResults. */
    def onlyNewest : SearchResultResult = {
      from(query)((q) =>
        select(q)
        orderBy(q.createdAt desc)
      ).page(0, 20)
    }
  }
  object SearchResultResult {
    implicit def fromQuery(query: Query[SearchResult]) = new SearchResultResult(query)
  }

  /** @return All `SearchResult`s with the given DocumentSet. */
  def byDocumentSet(documentSet: Long) : SearchResultResult = {
    Schema.searchResults.where(_.documentSetId === documentSet)
  }

  /** @return Zero or one `SearchResult`. */
  def byDocumentSetAndId(documentSet: Long, id: Long) : SearchResultResult = {
    Schema.searchResults.where((sr) => sr.documentSetId === documentSet and sr.id === id)
  }
}
