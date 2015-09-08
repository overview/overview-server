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
        job_id,
        title,
        document_count,
        lang,
        supplied_stop_words,
        important_words,
        description,
        created_at
      )
      SELECT
        ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & id),
        $cloneDocumentSetId,
        ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & root_node_id),
        0,
        title,
        document_count,
        lang,
        supplied_stop_words,
        important_words,
        description,
        CLOCK_TIMESTAMP()
      FROM tree
      WHERE document_set_id = $sourceDocumentSetId
    """)
  }
}
