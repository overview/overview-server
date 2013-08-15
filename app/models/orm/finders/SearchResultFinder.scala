package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.SearchResult
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

import models.orm.Schema

object SearchResultFinder extends Finder {
  /** @return All `SearchResult`s with the given DocumentSet. */
  def byDocumentSet(documentSet: Long) : FinderResult[SearchResult] = {
    Schema.searchResults.where(_.documentSetId === documentSet)
  }

  /** @return Zero or one `SearchResult`. */
  def byDocumentSetAndId(documentSet: Long, id: Long) : FinderResult[SearchResult] = {
    Schema.searchResults.where((sr) => sr.documentSetId === documentSet and sr.id === id)
  }
}
