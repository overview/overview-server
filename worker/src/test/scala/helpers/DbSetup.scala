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
  
  def insertNode(documentSetId: Long)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO node (description, document_set_id) VALUES 
        ('description', {documentSetId})
        """).on("documentSetId" -> documentSetId).executeInsert().getOrElse(failInsert)
  }

  def insertDocument(documentSetId: Long, 
                     title: String, textUrl: String, viewUrl: String)
                    (implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document(document_set_id, title, text_url, view_url) VALUES 
          ({documentSetId}, {title}, {textUrl}, {viewUrl})
        """).on("documentSetId" -> documentSetId,
      "title" -> title, "textUrl" -> textUrl, "viewUrl" -> viewUrl).
      executeInsert().getOrElse(failInsert)
  }
  
  def insertDocuments(documentSetId: Long, count: Int)
                     (implicit c: Connection): Seq[Long] = {
    for (i <- 1 to count) yield 
      insertDocument(documentSetId, "title-" + i, "textUrl-" + i, "viewUrl-" + i)
  }

}
