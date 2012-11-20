package persistence

import anorm._
import java.sql.Connection

trait PersistentCsvImportDocument {
  val text: String
  val title: String = ""
  
  def write(documentSetId: Long)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document (type, text, title, document_set_id) VALUES
          ('CsvImportDocument', {text}, {title}, {documentSetId})
        """).on("text" -> text, "title" -> title, "documentSetId" -> documentSetId).executeInsert().get
  }
}