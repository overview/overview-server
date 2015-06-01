package org.overviewproject.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.jdbc.StaticQuery

import org.overviewproject.database.{SlickClient,SlickSessionProvider}

trait SortedDocumentIdsRefresher extends SlickClient {
  def refreshDocumentSet(documentSetId: Long): Future[Unit] = db { session =>
    val q = StaticQuery.update[Long]("""
      UPDATE document_set
      SET sorted_document_ids = (
        SELECT COALESCE(ARRAY_AGG(id ORDER BY title, supplied_id, page_number, id), '{}')
        FROM document
        WHERE document_set_id = document_set.id
      )
      WHERE id = ?
    """)
    q(documentSetId).execute(session)
  }
}

object SortedDocumentIdsRefresher extends SortedDocumentIdsRefresher with SlickSessionProvider
