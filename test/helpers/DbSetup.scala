package helpers

import anorm._
import java.sql.Connection

object DbSetup {
  
  private def failInsert = { throw new Exception("failed insert") }
  
  def insertDocumentSet(query: String)(implicit c: Connection) : Long = {
    SQL("""
      INSERT INTO document_set (id, query)
      VALUES (nextval('document_set_seq'), {query})
      """).on("query" -> query).
           executeInsert().getOrElse(failInsert)
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
      ).executeInsert().getOrElse(failInsert)
  }

  def insertDocument(documentSetId: Long, title: String, textUrl: String, viewUrl: String)
                     (implicit connection: Connection) : Long = {
    SQL("""
        INSERT INTO document(id, document_set_id, title, text_url, view_url) VALUES 
          (nextval('document_seq'), {documentSetId}, {title}, {textUrl}, {viewUrl})
        """).on("documentSetId" -> documentSetId,
                "title" -> title, "textUrl" -> textUrl,"viewUrl" -> viewUrl).
             executeInsert().getOrElse(failInsert)
  }
  
  def insertNodeDocument(nodeId: Long, documentId: Long)(implicit c: Connection) : Long = {
    SQL("""
        INSERT INTO node_document(node_id, document_id) VALUES ({nodeId}, {documentId})
        """).on("nodeId" -> nodeId, "documentId" -> documentId).
             executeInsert().getOrElse(failInsert)
  }
  
  def insertTag(documentSetId: Long, name: String)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO tag (id, name, document_set_id)
        VALUES (nextval('tag_seq'), {name}, {documentSetId})
        """).on("name" -> name, "documentSetId" -> documentSetId).
        executeInsert().getOrElse(failInsert)
  }

  def insertDocumentWithNode(documentSetId: Long,
                             title: String, textUrl: String, viewUrl: String, 
                             nodeId: Long)(implicit connection: Connection) : Long = {
    val documentId = insertDocument(documentSetId, title, textUrl, viewUrl)
    insertNodeDocument(nodeId, documentId)
    
    documentId
  }
  
  def insertNodes(documentSetId: Long, count: Int)(implicit c: Connection): Seq[Long] = {
    for (i <- 1 to count) yield insertNode(documentSetId, None, "node-" + i)
  }

  def insertDocumentsForeachNode(documentSetId: Long, nodeIds: Seq[Long], 
                                 documentCount: Int)(implicit c: Connection): Seq[Long] = {

    nodeIds.flatMap(n =>
      for (i <- 1 to documentCount) yield insertDocumentWithNode(documentSetId,
        "title-" + i, "textUrl-" + i, "viewUrl-" + i,
        n))
  }
  
  def tagDocuments(tagId: Long, documentIds: Seq[Long])(implicit c: Connection) : Long = {
    SQL("""
        INSERT INTO document_tag (document_id, tag_id)
        SELECT id, {tagId} FROM document
        WHERE id IN """ + documentIds.mkString("(", ",", ")")
        ).on("tagId" -> tagId).executeUpdate()
  }


}