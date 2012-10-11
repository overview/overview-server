/*
 * DbSetup.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package helpers

import anorm._
import java.sql.Connection

/**
 * Helper functions to setup database for tests. Very similar to DbSetup.scala 
 * in overview-server.
 */
object DbSetup {

  private def failInsert = { throw new Exception("failed insert") }

  def insertDocumentSet(query: String)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document_set (title, query) 
        VALUES({title}, {query})
        """).on(
          'title -> ("DocumentSet for " + query),
          'query -> query
        ).executeInsert().getOrElse(failInsert)
  }
  
  def insertNode(documentSetId: Long, parentId: Option[Long], description: String)
                (implicit c: Connection) : Long = {
    SQL("""
      INSERT INTO node (document_set_id, parent_id, description)
      VALUES ({document_set_id}, {parent_id}, {description})
      """).on(
        'document_set_id -> documentSetId,
        'parent_id -> parentId,
        'description -> description
      ).executeInsert().getOrElse(failInsert)
  }

  def insertNodeDocument(nodeId: Long, documentId: Long)(implicit c: Connection) : Long = {
    SQL("""
        INSERT INTO node_document(node_id, document_id) VALUES ({nodeId}, {documentId})
        """).on("nodeId" -> nodeId, "documentId" -> documentId).
             executeInsert().getOrElse(failInsert)
  }

  def insertDocument(documentSetId: Long, title: String, documentCloudId: String)
                    (implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document(document_set_id, title, documentcloud_id) VALUES 
          ({documentSetId}, {title}, {documentCloudId})
        """).on("documentSetId" -> documentSetId,
      "title" -> title, "documentCloudId" -> documentCloudId).
      executeInsert().getOrElse(failInsert)
  }
  
  def insertDocuments(documentSetId: Long, count: Int)
                     (implicit c: Connection): Seq[Long] = {
    for (i <- 1 to count) yield 
      insertDocument(documentSetId, "title-" + i, "documentCloudId-" + i)
  }

}
