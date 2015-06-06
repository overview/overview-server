package org.overviewproject.clone

import anorm._
import anorm.SqlParser._
import java.sql.Connection
import scala.language.postfixOps

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.util.Logger

object DocumentCloner extends InDatabaseCloner {

  override def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Boolean = {
    val cloneResult = super.clone(sourceDocumentSetId, cloneDocumentSetId)

    Logger.debug("updating ref counts")
    updateFileRefCounts(cloneDocumentSetId)
    Logger.debug("done with ref counts")
    cloneResult
  }

  override def cloneQuery: SqlQuery =
    SQL("""
          INSERT INTO document 
            (id, document_set_id,
             description, documentcloud_id, text, url, supplied_id, title,
             created_at, file_id, page_id)
           SELECT 
             ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & id) AS clone_id, 
             {cloneDocumentSetId} AS clone_document_set_id, 
             description, documentcloud_id, text, url, supplied_id, title,
             created_at, file_id, page_id
             FROM document WHERE document_set_id = {sourceDocumentSetId}    	
        """)

  private def updateFileRefCounts(cloneDocumentSetId: Long): Unit = {
    implicit val c: Connection = DeprecatedDatabase.currentConnection

    SQL("""
      WITH file_ids AS (
        SELECT file_id
        FROM document
        WHERE document_set_id = {documentSetId}
          AND file_id IS NOT NULL
        FOR UPDATE
      )
      UPDATE file
      SET reference_count = reference_count + 1
      WHERE EXISTS (SELECT 1 FROM file_ids WHERE file_id = file.id)
      AND reference_count > 0
    """).on('documentSetId -> cloneDocumentSetId).executeUpdate
  }
}
