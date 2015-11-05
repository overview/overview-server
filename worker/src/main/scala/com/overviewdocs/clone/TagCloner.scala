package com.overviewdocs.clone

import com.overviewdocs.database.HasBlockingDatabase

object TagCloner extends HasBlockingDatabase {
  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Map[Long, Long] = {
    import database.api._

    blockingDatabase.run(sql"""
      WITH old_and_new AS (
        SELECT id AS old_id, nextval('tag_id_seq'::regclass) AS new_id, name, color
        FROM tag
        WHERE document_set_id = $sourceDocumentSetId
      ), x_new_rows AS (
        INSERT INTO tag (id, document_set_id, name, color)
        SELECT new_id, $cloneDocumentSetId, name, color
        FROM old_and_new
      )
      SELECT old_id, new_id FROM old_and_new
    """.as[(Long,Long)]).toMap
  }
}
