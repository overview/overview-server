package com.overviewdocs.clone

object NodeCloner extends InDatabaseCloner {
  import database.api._

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long) = {
    logger.debug("Cloning Nodes from {} to {}", sourceDocumentSetId, cloneDocumentSetId)

    blockingDatabase.runUnit(sqlu"""
      INSERT INTO node (id, root_id, parent_id, description, cached_size, is_leaf)
      SELECT
        ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & id),
        ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & root_id),
        ($cloneDocumentSetId << 32) | ($DocumentSetIdMask & parent_id),
        description,
        cached_size,
        is_leaf
      FROM node
      WHERE id >= ($sourceDocumentSetId << 32)
        AND id < (($sourceDocumentSetId + 1) << 32)
    """)
  }
}
