package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

case class DocumentSet(documentSetType: String, title: String, query: Option[String] = None, uploadedFileId: Option[Long] = None)

object DocumentSetLoader {

  def load(documentSetId: Long)(implicit c: Connection): Option[DocumentSet] = {
    SQL("""
        SELECT type::VARCHAR, title, query, uploaded_file_id FROM document_set
        WHERE id = {documentSetId}
        """).on("documentSetId" -> documentSetId).
      as(str("type") ~ str("title") ~ (str("query")?) ~ (long("uploaded_file_id")?) map (flatten) *).headOption map {
      case (documentSetType, title, query, uploadedFileId) => DocumentSet(documentSetType, title, query, uploadedFileId)
    }
  }
}
