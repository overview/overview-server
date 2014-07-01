package org.overviewproject.clone

import anorm._

object NodeCloner extends InDatabaseCloner {

	override def cloneQuery: SqlQuery = 
    SQL("""
        INSERT INTO node (id, tree_id, parent_id, description, cached_size, is_leaf)
        SELECT
          ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & id) AS clone_id,
          ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & tree_id) AS tree_id,
          ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & parent_id) AS clone_parent_id,
          description,
          cached_size,
          is_leaf
        FROM node
        WHERE tree_id IN (SELECT id FROM tree WHERE document_set_id = {sourceDocumentSetId})
        """)

}
