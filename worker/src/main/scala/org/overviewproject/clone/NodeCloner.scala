package org.overviewproject.clone

import anorm._

object NodeCloner extends InDatabaseCloner {

	override def cloneQuery: SqlQuery = 
    SQL("""
        WITH 
          cached_document_ids AS 
            (SELECT id AS node_id, unnest(cached_document_ids) AS document_id FROM node WHERE 
               tree_id IN (SELECT id FROM tree WHERE document_set_id = {sourceDocumentSetId})),
          cloned_document_ids AS
            (SELECT node_id, ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & document_id) AS clone_id FROM cached_document_ids),
          cloned_cache AS
            (SELECT node_id, array_agg(clone_id) AS cached_document_ids FROM cloned_document_ids GROUP BY node_id)

        INSERT INTO node (id, tree_id, parent_id, description, cached_document_ids, cached_size, is_leaf)
          SELECT
            ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & id) AS clone_id,
            ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & tree_id) AS tree_id,
            ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & parent_id) AS clone_parent_id,
            description,
            cloned_cache.cached_document_ids,
            cached_size,
            is_leaf
          FROM node, cloned_cache WHERE tree_id IN 
            (SELECT id FROM tree WHERE document_set_id = {sourceDocumentSetId})
            AND node.id = node_id
        """)

}
