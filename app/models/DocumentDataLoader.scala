package models

import scala.language.postfixOps
import anorm.SQL
import anorm.SqlParser._
import java.sql.Connection
import models.DatabaseStructure.DocumentData

class DocumentDataLoader {
  def loadDocument(id: Long)(implicit c: Connection) : Option[DocumentData] = {
    val documentParser = long("id") ~ str("description") ~ get[Option[String]]("documentcloud_id") ~ get[Option[String]]("title")

    val document = SQL("""
        SELECT id, description, documentcloud_id, title
        FROM document
        WHERE id = {documentId}
        """).on("documentId" -> id).
        as(documentParser map(flatten) *)

    document.headOption
  }
}
