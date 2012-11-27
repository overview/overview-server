/*
 * PeristentDocumentCloudDocument.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package persistence

import anorm._
import java.sql.Connection

/**
 * Writes a DocumentCloud document to that database document table
 */
trait PersistentDocumentCloudDocument {
  val title: String
  val documentCloudId: String
  
  /** write the document to the database with the specified documentSetId */
  def write(documentSetId: Long)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document (type, title, documentcloud_id, document_set_id) VALUES
          ('DocumentCloudDocument'::document_type, {title}, {documentCloudId}, {documentSetId})
        """).on("title" -> title, "documentCloudId" -> documentCloudId,
                "documentSetId" -> documentSetId).executeInsert().get
  }
}
