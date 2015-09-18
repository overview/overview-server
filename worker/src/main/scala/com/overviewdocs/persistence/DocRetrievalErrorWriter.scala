package com.overviewdocs.persistence

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.documentcloud.DocumentRetrievalError
import com.overviewdocs.models.DocumentProcessingError
import com.overviewdocs.models.tables.DocumentProcessingErrors

object DocRetrievalErrorWriter extends HasBlockingDatabase {
  private def headersToString(headers: Map[String,Seq[String]]): String = {
    headers.toSeq
      .map { case (key, values) => values.map { value => s"$key: $value" } }
      .flatten
      .mkString("\r\n")
  }

  def write(documentSetId: Long, errors: Seq[DocumentRetrievalError]) {
    import database.api._

    val toInsert = errors.map(e => DocumentProcessingError.CreateAttributes(
      documentSetId,
      e.url,
      e.message,
      e.statusCode,
      e.headers.map(headersToString)
    ))

    blockingDatabase.runUnit(DocumentProcessingErrors.map(_.createAttributes).++=(toInsert))

    blockingDatabase.run(sqlu"""
      UPDATE document_set
      SET document_processing_error_count = (
        SELECT COUNT(*) FROM document_processing_error WHERE document_set_id = document_set.id
      )
      WHERE id = $documentSetId
    """)
  }
}
