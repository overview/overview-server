package org.overviewproject.clone

import anorm._

object NodeCloner extends InDatabaseCloner {

	override def cloneQuery: SqlQuery = 
    SQL("""
        INSERT INTO node (id, root_id, parent_id, description, cached_size, is_leaf)
        SELECT
          ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & id),
          ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & root_id),
          ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & parent_id),
          description,
          cached_size,
          is_leaf
        FROM node
        WHERE id >= ({sourceDocumentSetId} << 32) AND id < (({sourceDocumentSetId} + 1) << 32)
        """)

}
