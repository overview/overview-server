package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

object DocumentSetLoader {

  def loadQuery(documentSetId: Long)(implicit c: Connection): Option[String] = {
    SQL("""
        SELECT query FROM document_set
        WHERE id = {documentSetId}
        """).on("documentSetId" -> documentSetId).as(str("query") singleOpt)
        
  }
}