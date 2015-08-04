package com.overviewdocs.util

import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase

trait SortedDocumentIdsRefresher extends HasDatabase {
  import database.api._

  def refreshDocumentSet(documentSetId: Long): Future[Unit] = database.runUnit(sqlu"""
    UPDATE document_set
    SET sorted_document_ids = (
      SELECT COALESCE(ARRAY_AGG(id ORDER BY title, supplied_id, page_number, id), '{}')
      FROM document
      WHERE document_set_id = document_set.id
    )
    WHERE id = $documentSetId
  """)
}

object SortedDocumentIdsRefresher extends SortedDocumentIdsRefresher
