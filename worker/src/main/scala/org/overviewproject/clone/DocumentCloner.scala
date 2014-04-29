package org.overviewproject.clone

import scala.language.postfixOps
import anorm._
import anorm.SqlParser._
import java.sql.Connection
import org.overviewproject.database.Database
import org.overviewproject.util.Logger

object DocumentCloner extends InDatabaseCloner {

  override def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Boolean = {
    val cloneResult = super.clone(sourceDocumentSetId, cloneDocumentSetId)

    Logger.debug("updating ref counts")
    updateFileRefCounts(sourceDocumentSetId)
    Logger.debug("done with ref counts")
    cloneResult
  }

  override def cloneQuery: SqlQuery =
    SQL("""
          INSERT INTO document 
            (id, document_set_id, 
             description, documentcloud_id, text, url, supplied_id, title, file_id, page_id, content_length)
           SELECT 
             ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & id) AS clone_id, 
             {cloneDocumentSetId} AS clone_document_set_id, 
             description, documentcloud_id, text, url, supplied_id, title, file_id, page_id, content_length
             FROM document WHERE document_set_id = {sourceDocumentSetId}    	
        """)

  private def updateFileRefCounts(sourceDocumentSetId: Long): Unit = {
    implicit val c: Connection = Database.currentConnection

    SQL("""
        SELECT * FROM file 
          WHERE id IN 
            (SELECT file_id FROM document
               WHERE document_set_id = {documentSetId})
          ORDER BY id
          FOR UPDATE
        """).on("documentSetId" -> sourceDocumentSetId).execute

    
    SQL("""
    	UPDATE file SET reference_count = reference_count + 1
          WHERE id IN 
            (SELECT file_id FROM document WHERE document_set_id = {documentSetId})
    	    """).on("documentSetId" -> sourceDocumentSetId).executeUpdate
    	    
    SQL("""
        UPDATE page SET reference_count = reference_count + 1
          WHERE file_id IN
            (SELECT file_id FROM document WHERE document_set_id = {documentSetId})
        """).on("documentSetId" -> sourceDocumentSetId).executeUpdate
  }
}