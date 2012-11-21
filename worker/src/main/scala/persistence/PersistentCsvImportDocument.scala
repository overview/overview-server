package persistence

import anorm._
import java.sql.Connection

trait PersistentCsvImportDocument {
  val text: String
  val title: String = ""
  val suppliedId: Option[String]
  
  def write(documentSetId: Long)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document (type, text, title, supplied_id, document_set_id) VALUES
          ('CsvImportDocument', {text}, {title}, {suppliedId}, {documentSetId})
        """).on("text" -> text, "title" -> title,  "suppliedId" -> suppliedId, "documentSetId" -> documentSetId).executeInsert().get
  }
}