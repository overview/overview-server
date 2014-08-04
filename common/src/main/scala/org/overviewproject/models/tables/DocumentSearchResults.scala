package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.tree.orm.DocumentSearchResult // should be models.DocumentSearchResult

class DocumentSearchResultsImpl(tag: Tag) extends Table[DocumentSearchResult](tag, "document_search_result") {
  def documentId = column[Long]("document_id")
  def searchResultId = column[Long]("search_result_id")
  def pk = primaryKey("document_search_result_pkey", (documentId, searchResultId))

  def * = (
    documentId,
    searchResultId
  ) <> (DocumentSearchResult.tupled, DocumentSearchResult.unapply)
}

object DocumentSearchResults extends TableQuery(new DocumentSearchResultsImpl(_))
