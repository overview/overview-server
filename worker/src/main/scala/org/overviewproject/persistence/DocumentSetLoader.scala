package org.overviewproject.persistence

import scala.language.postfixOps
import anorm._
import anorm.SqlParser._
import java.sql.Connection

case class DocumentSet(title: String, query: Option[String] = None, uploadedFileId: Option[Long] = None)

object DocumentSetLoader {

  def load(documentSetId: Long)(implicit c: Connection): Option[DocumentSet] = {
    // TODO use Squeryl? anorm gains us nothing
    SQL("""
        SELECT title, query, uploaded_file_id FROM document_set
        WHERE id = {documentSetId}
        """).on("documentSetId" -> documentSetId).
      as(str("title") ~ (str("query")?) ~ (long("uploaded_file_id")?) map (flatten) *).headOption map {
      case (title, query, uploadedFileId) => DocumentSet(title=title, query=query, uploadedFileId=uploadedFileId)
    }
  }
}
