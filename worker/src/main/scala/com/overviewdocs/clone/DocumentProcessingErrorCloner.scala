package com.overviewdocs.clone

object DocumentProcessingErrorCloner extends InDatabaseCloner {
  import database.api._

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) {
    logger.debug("Cloning DocumentProcessingErrors from {} to {}", sourceDocumentSetId, cloneDocumentSetId)

    blockingDatabase.runUnit(sqlu"""
      INSERT INTO document_processing_error (
        document_set_id,
        text_url,
        message,
        status_code,
        headers
      )
      SELECT
        $cloneDocumentSetId,
        text_url,
        message,
        status_code,
        headers
      FROM document_processing_error
      WHERE document_set_id = $sourceDocumentSetId
    """)
  }
}
