package models

import anorm._
import anorm.SqlParser._
import helpers.DbTestContext
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }

class PersistentDocumentListDataLoaderSpec extends Specification {

  step(start(FakeApplication()))

  "PersistentDocumentListDataLoader" should {

    "load document data for specified nodes" in new DbTestContext {
      val documentSet =
        SQL("""
            INSERT INTO document_set (id, query)
            VALUES (nextval('document_set_seq'), 'PersistentDocumentListLoaderSpec')
            """).executeInsert()

      val nodeIds =
        SQL("""
           INSERT INTO node (id, description, document_set_id)
           VALUES (nextval('node_seq'), 'node1', {document_set_id}),
    		   	   (nextval('node_seq'), 'node2', {document_set_id}),
                   (nextval('node_seq'), 'node3', {document_set_id})
           """).on("document_set_id" -> documentSet).executeInsert(scalar[Long] *)

      val documentIds = nodeIds.flatMap { n =>
        val documentIds =
          SQL("""
              INSERT INTO document (id, title, text_url, view_url, document_set_id)
              VALUES (nextval('document_seq'),
                      'title', 'textUrl', 'viewUrl', {document_set_id}),
                     (nextval('document_seq'),
                      'title', 'textUrl', 'viewUrl', {document_set_id})
              """).on("document_set_id" -> documentSet).executeInsert(scalar[Long] *)

        documentIds.foreach { d =>
          SQL("""
                INSERT INTO node_document (node_id, document_id)
                VALUES ({node_id}, {document_id})
                """).on("node_id" -> n, "document_id" -> d).executeInsert()
        }
        
        documentIds
      }
      
      val persistentDocumentListDataLoader =
        new PersistentDocumentListDataLoader(nodeIds, Nil)
      
      val documentData = persistentDocumentListDataLoader.loadDocumentSlice(0, 6)
      
      documentData must have size(6)
    }
  }

  step(stop)
}