package org.overviewproject.clone

import java.sql.Connection
import anorm.SQL
import org.overviewproject.persistence.DocumentSetIdGenerator
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.database.Database




object DocumentCloner {
  private val DocumentSetIdMask: Long = 0x00000000FFFFFFFFl
  
  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Boolean = {
    implicit val c: Connection = Database.currentConnection  

    SQL("""
          INSERT INTO document (id, document_set_id, description, documentcloud_id, type, text, url, supplied_id, title)
           SELECT 
             ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & id) AS clone_id, 
             {cloneDocumentSetId} AS clone_document_set_id, 
             description, documentcloud_id, type, text, url, supplied_id, title 
             FROM document WHERE document_set_id = {sourceDocumentSetId}    	
        """).on("cloneDocumentSetId" -> cloneDocumentSetId,
            "sourceDocumentSetId" -> sourceDocumentSetId, 
            "documentSetIdMask" -> DocumentSetIdMask).execute
  }
}