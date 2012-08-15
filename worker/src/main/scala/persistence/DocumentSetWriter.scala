/*
 * DocumentSetWriter
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import java.sql.Connection

/**
 * Writes out the query defining a document set to the database.
 */
class DocumentSetWriter() {
  
  def write(query: String)(implicit c: Connection) : Long = {
    SQL("""
        INSERT INTO document_set (id, query) 
        VALUES (nextval('document_set_seq'), {query})
        """).on("query" -> query).executeInsert().get
  }
}