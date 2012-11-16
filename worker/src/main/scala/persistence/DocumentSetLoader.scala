package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

case class DocumentSet(documentSetType: String, title: String, query: Option[String])

object DocumentSetLoader {

  def loadQuery(documentSetId: Long)(implicit c: Connection): Option[DocumentSet] = {
    SQL("""
        SELECT title, query FROM document_set
        WHERE id = {documentSetId}
        """).on("documentSetId" -> documentSetId).
      as(str("title") ~ (str("query")?) map (flatten) *).headOption map {
      case (title, query) => DocumentSet("DocumentCloudDocumentSet", title, query)
    }
  }
}
