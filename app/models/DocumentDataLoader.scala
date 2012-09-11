package models

import anorm.SQL
import anorm.SqlParser._
import java.sql.Connection
import models.DatabaseStructure.DocumentData

class DocumentDataLoader {
  def loadDocument(id: Long)(implicit c: Connection) : Option[DocumentData] = {
    val documentParser = long("id") ~ str("title") ~ str("documentcloud_id") 
    
    val document = SQL("""
        SELECT id, title, documentcloud_id
        FROM document
        WHERE id = {documentId}
        """).on("documentId" -> id).
        as(documentParser map(flatten) *)
        
    document.headOption
  }
}
