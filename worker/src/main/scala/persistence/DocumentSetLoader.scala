package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

object DocumentSetLoader {

  def loadQuery(documentSetId: Long)(implicit c: Connection): Option[(String, String)] = {
    SQL("""
        SELECT title, query FROM document_set
        WHERE id = {documentSetId}
        """).on("documentSetId" -> documentSetId).
      as(str("title") ~ str("query") map (flatten) *).headOption
  }
}
