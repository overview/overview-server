/*
 * PersistentCsvImportDocument.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package persistence

import anorm._
import java.sql.Connection

/**
 * Writes a CsvImportDocument document to the database document table.
 * Stores the suppliedId value if provided.
 */
trait PersistentCsvImportDocument {
  val text: String
  val title: String = ""
  val suppliedId: Option[String]
  
  /** writes the document to the database document table, with the given documentSetId */
  def write(documentSetId: Long)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document (type, text, title, supplied_id, document_set_id) VALUES
          ('CsvImportDocument'::document_type, {text}, {title}, {suppliedId}, {documentSetId})
        """).on("text" -> text, "title" -> title,  "suppliedId" -> suppliedId, "documentSetId" -> documentSetId).executeInsert().get
  }
}
