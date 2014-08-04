package org.overviewproject.models.tables

import java.sql.Timestamp

import org.overviewproject.database.Slick.simple._
import org.overviewproject.tree.orm.{SearchResult,SearchResultState} // should be models.SearchResult

class SearchResultsImpl(tag: Tag) extends Table[SearchResult](tag, "search_result") {
  private implicit val searchResultColumnType = MappedColumnType.base[SearchResultState.Value, Int](
    { v => v.id },
    { i => SearchResultState(i) }
  )

  def id = column[Long]("id", O.PrimaryKey)
  def query = column[String]("query")
  def state = column[SearchResultState.Value]("state")
  def documentSetId = column[Long]("document_set_id")
  def createdAt = column[Timestamp]("created_at")

  def * = (state, documentSetId, query, createdAt, id) <> (SearchResult.tupled, SearchResult.unapply)
}

object SearchResults extends TableQuery(new SearchResultsImpl(_))
