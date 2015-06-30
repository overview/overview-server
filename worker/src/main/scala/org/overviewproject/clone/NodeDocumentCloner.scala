package org.overviewproject.clone

object NodeDocumentCloner extends InDatabaseCloner {
  import database.api._

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) = {
    logger.debug("Cloning NodeDocuments from {} to {}", sourceDocumentSetId, cloneDocumentSetId)

    blockingDatabase.runUnit(sqlu"""
      INSERT INTO node_document (node_id, document_id)
        SELECT
          ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & node_id),
          ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & document_id)
        FROM node_document
        INNER JOIN document ON 
          (document.document_set_id = $sourceDocumentSetId AND
           node_document.document_id = document.id)
    """)
  }
}
