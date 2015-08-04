package com.overviewdocs.clone

object DocumentTagCloner extends InDatabaseCloner {
  import database.api._

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long, tagMapping: Map[Long, Long]): Unit = {
    logger.debug("Cloning DocumentTags from {} to {}", sourceDocumentSetId, cloneDocumentSetId)

    if (tagMapping.nonEmpty) {
      val values: String = tagMapping // Map[Long,Long]
        .toSeq // Seq[(Long,Long)]
        .map(t => "(" + t._1 + "," + t._2 + ")")
        .mkString(",")

      blockingDatabase.runUnit(sqlu"""
        WITH tag_pairs AS (
          SELECT *
          FROM (VALUES #$values) AS t(source_tag_id, clone_tag_id)
        )
        INSERT INTO document_tag (document_id, tag_id)
        SELECT
          ($cloneDocumentSetId << 32) | (document_id & $DocumentSetIdMask),
          (SELECT clone_tag_id FROM tag_pairs WHERE source_tag_id = tag_id)
        FROM document_tag
        WHERE document_id >= ($sourceDocumentSetId << 32)
          AND document_id < (($sourceDocumentSetId + 1) << 32)
      """)
    }
  }
}
