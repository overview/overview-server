/*
 * DocumentWriter.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import java.sql.Connection

/**
 * Writes out document information associated with the documentSetId
 */
class DocumentWriter(documentSetId: Long) {
  
 
  def write(title: String, textUrl: String, viewUrl: String)
           (implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document (title, text_url, view_url, document_set_id) VALUES 
          ({title}, {textUrl}, {viewUrl}, {documentSetId})
        """).on("title" -> title, "textUrl" -> textUrl, "viewUrl" -> viewUrl, 
                "documentSetId" -> documentSetId).executeInsert().get
  }
}