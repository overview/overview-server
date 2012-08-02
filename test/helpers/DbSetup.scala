package helpers

import anorm._
import java.sql.Connection

object DbSetup {
  
  def insertDocumentSet(query: String)(implicit c: Connection) : Long = {
    SQL("""
      INSERT INTO document_set (id, query)
      VALUES (nextval('document_set_seq'), {query})
      """).on("query" -> query).
           executeInsert().getOrElse(throw new Exception("failed insert"))
  }

  def insertNode(documentSetId: Long, parentId: Option[Long], description: String)
                (implicit c: Connection) : Long = {
    SQL("""
      INSERT INTO node (id, document_set_id, parent_id, description)
      VALUES (nextval('node_seq'), {document_set_id}, {parent_id}, {description})
      """).on(
        'document_set_id -> documentSetId,
        'parent_id -> parentId,
        'description -> description
      ).executeInsert().getOrElse(throw new Exception("failed insert"))
  }

  def insertDocument(title: String, textUrl: String, viewUrl: String)
                     (implicit connection: Connection) : Long = {
    SQL("""
        INSERT INTO document(id, title, text_url, view_url) VALUES 
          (nextval('document_seq'), {title}, {textUrl}, {viewUrl})
        """).on("title" -> title, "textUrl" -> textUrl,"viewUrl" -> viewUrl).
             executeInsert().getOrElse(throw new Exception("failed insert"))
  }
  
  def insertNodeDocument(nodeId: Long, documentId: Long)(implicit c: Connection) : Long = {
    SQL("""
        INSERT INTO node_document(node_id, document_id) VALUES ({nodeId}, {documentId})
        """).on("nodeId" -> nodeId, "documentId" -> documentId).
             executeInsert().getOrElse(throw new Exception("failed insert"))
  }
  
  def insertDocumentWithNode(title: String, textUrl: String, viewUrl: String, nodeId: Long)
                            (implicit connection: Connection) : Long = {
    val documentId = insertDocument(title, textUrl, viewUrl)
    insertNodeDocument(nodeId, documentId)
    
    documentId
  }
  
  def insertNodes(documentSetId: Long, count: Int)(implicit c: Connection): Seq[Long] = {
    for (i <- 1 to 3) yield insertNode(documentSetId, None, "node-" + i)
  }

}