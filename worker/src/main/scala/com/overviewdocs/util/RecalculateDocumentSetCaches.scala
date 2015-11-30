package com.overviewdocs.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase

object RecalculateDocumentSetCaches extends HasDatabase {
  import database.api._

  def run(documentSetId: Long): Future[Unit] = {
    for {
      _ <- SortedDocumentIdsRefresher.refreshDocumentSet(documentSetId)
      _ <- updateDocumentSetCounts(documentSetId)
    } yield ()
  }

  private def updateDocumentSetCounts(documentSetId: Long): Future[Unit] = {
    import database.api._
    database.runUnit(sqlu"""
      UPDATE document_set
      SET document_count = (SELECT COUNT(*) FROM document WHERE document_set_id = document_set.id),
          document_processing_error_count = (SELECT COUNT(*) FROM document_processing_error WHERE document_set_id = document_set.id)
      WHERE id = ${documentSetId}
    """)
  }
}
