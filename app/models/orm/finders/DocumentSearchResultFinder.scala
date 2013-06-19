package models.orm.finders

import models.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentSearchResult


object DocumentSearchResultFinder extends Finder {
  type DocumentSearchResultFinderResult = FinderResult[DocumentSearchResult]
  
  def bySearchResult(searchResult: Long): DocumentSearchResultFinderResult =
    Schema.documentSearchResults.where(_.searchResultId === searchResult)
}