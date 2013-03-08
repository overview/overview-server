package org.overviewproject.clone

import anorm._

object DocumentCloner extends InDatabaseCloner {

  override def cloneQuery: SqlQuery = 
    SQL("""
          INSERT INTO document (id, document_set_id, description, documentcloud_id, type, text, url, supplied_id, title)
           SELECT 
             ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & id) AS clone_id, 
             {cloneDocumentSetId} AS clone_document_set_id, 
             description, documentcloud_id, type, text, url, supplied_id, title 
             FROM document WHERE document_set_id = {sourceDocumentSetId}    	
        """)
}