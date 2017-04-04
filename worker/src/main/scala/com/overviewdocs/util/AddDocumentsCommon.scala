package com.overviewdocs.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.searchindex.{ElasticSearchIndexClient,IndexClient}

trait AddDocumentsCommon extends HasDatabase {
  protected val indexClient: IndexClient

  import database.api._

  /** Adds the document set to the search index, if it isn't already present.
      Also clears the document processing errors list.
    */
  def beforeAddDocuments(documentSetId: Long): Future[Unit] = {
    // for/yeild on futures means run in parallel if possible
    for {
      _ <- database.runUnit(sqlu"""
        DELETE FROM document_processing_error
        WHERE document_set_id = ${documentSetId}
      """)
      _ <- indexClient.addDocumentSet(documentSetId)
    } yield ()
  }

  /** Updates `document_set.document_count`, `.document_processing_error_count`
    * and `.sorted_document_ids` for the given document set.
    */
  def afterAddDocuments(documentSetId: Long): Future[Unit] = {
    for {
      _ <- database.runUnit(sqlu"""
        UPDATE document_set
        SET document_count = (SELECT COUNT(*) FROM document WHERE document_set_id = document_set.id),
            document_processing_error_count = (SELECT COUNT(*) FROM document_processing_error WHERE document_set_id = document_set.id),
            sorted_document_ids = (
              SELECT COALESCE(ARRAY_AGG(id ORDER BY title, supplied_id, page_number, id), '{}')
              FROM document
              WHERE document_set_id = document_set.id
            )
        WHERE id = ${documentSetId}
      """)
      _ <- indexClient.refresh
    } yield ()
  }
}

object AddDocumentsCommon extends AddDocumentsCommon {
  override protected val indexClient = ElasticSearchIndexClient.singleton
}
