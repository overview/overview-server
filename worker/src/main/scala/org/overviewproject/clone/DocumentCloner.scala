package org.overviewproject.clone

import org.overviewproject.util.Logger

object DocumentCloner extends InDatabaseCloner {
  import database.api._
  import database.executionContext

  override def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) = {
    logger.debug("Cloning documents from {} to {} and updating file refcounts", sourceDocumentSetId, cloneDocumentSetId)

    blockingDatabase.runUnit((for {
      _ <- cloneQuery(sourceDocumentSetId, cloneDocumentSetId)
      _ <- updateFileRefCounts(cloneDocumentSetId)
    } yield ()).transactionally)
  }

  private def cloneQuery(sourceDocumentSetId: Long, cloneDocumentSetId: Long): DBIO[_] = sqlu"""
    INSERT INTO document 
      (id, document_set_id,
       description, documentcloud_id, text, url, supplied_id, title,
       created_at, file_id, page_id)
     SELECT 
       ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & id), $cloneDocumentSetId,
       description, documentcloud_id, text, url, supplied_id, title,
       created_at, file_id, page_id
     FROM document
     WHERE document_set_id = $sourceDocumentSetId
   """

  private def updateFileRefCounts(cloneDocumentSetId: Long): DBIO[_] = sqlu"""
    WITH file_ids AS (
      SELECT file_id
      FROM document
      WHERE document_set_id = $cloneDocumentSetId
        AND file_id IS NOT NULL
      FOR UPDATE
    )
    UPDATE file
    SET reference_count = reference_count + 1
    WHERE EXISTS (SELECT 1 FROM file_ids WHERE file_id = file.id)
    AND reference_count > 0
  """
}
