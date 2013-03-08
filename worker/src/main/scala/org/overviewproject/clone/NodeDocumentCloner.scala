package org.overviewproject.clone

import anorm._

object NodeDocumentCloner extends InDatabaseCloner {

  override def cloneQuery: SqlQuery =
    SQL("""
        INSERT INTO node_document (node_id, document_id)
          SELECT
            ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & node_id),
            ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & document_id)
          FROM node_document
          INNER JOIN document ON 
            (document.document_set_id = {sourceDocumentSetId} AND
             node_document.document_id = document.id)
        """)
}