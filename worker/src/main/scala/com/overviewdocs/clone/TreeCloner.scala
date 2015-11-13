package com.overviewdocs.clone

object TreeCloner extends InDatabaseCloner {
  import database.api._

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) = {
    logger.debug("Cloning Trees from {} to {}", sourceDocumentSetId, cloneDocumentSetId)

    blockingDatabase.runUnit(sqlu"""
      INSERT INTO tree (
        id,
        document_set_id,
        root_node_id,
        title,
        document_count,
        lang,
        supplied_stop_words,
        important_words,
        description,
        created_at,
        tag_id,
        progress,
        progress_description,
        cancelled
      )
      SELECT
        ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & id),
        $cloneDocumentSetId,
        ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & root_node_id),
        title,
        document_count,
        lang,
        supplied_stop_words,
        important_words,
        description,
        CLOCK_TIMESTAMP(),
        NULL,
        progress,
        progress_description,
        cancelled
      FROM tree
      WHERE document_set_id = $sourceDocumentSetId
    """)
  }
}
