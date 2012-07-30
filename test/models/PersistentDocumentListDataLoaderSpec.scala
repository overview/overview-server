package models

import anorm._
import anorm.SqlParser._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }


class PersistentDocumentListDataLoaderSpec extends Specification {

  step(start(FakeApplication()))

  "PersistentDocumentListDataLoader" should {

    def insertDocumentSet(implicit c: Connection): Long = {
      SQL("""
          INSERT INTO document_set (id, query)
          VALUES (nextval('document_set_seq'), 'PersistentDocumentListLoaderSpec')
          """).executeInsert().getOrElse(throw new Exception("failed insert"))
    }

    def setupNodes(documentSetId: Long)(implicit c: Connection): List[Long] = {
      SQL("""
         INSERT INTO node (id, description, document_set_id)
         VALUES (nextval('node_seq'), 'node1', {document_set_id}),
    		    (nextval('node_seq'), 'node2', {document_set_id}),
                (nextval('node_seq'), 'node3', {document_set_id})
         """).on("document_set_id" -> documentSetId).executeInsert(scalar[Long] *)
    }

    def insertDocument(nodeId: Long, documentSetId: Long)(implicit c: Connection): Long = {
      val documentId =
        SQL("""
        	INSERT INTO document (id, title, text_url, view_url, document_set_id)
            VALUES (nextval('document_seq'),
                    'title', 'textUrl', 'viewUrl', {document_set_id})
            """).on("document_set_id" -> documentSetId).executeInsert().
          getOrElse(throw new Exception("failed insert"))

      SQL("""
          INSERT INTO node_document (node_id, document_id)
          VALUES ({node_id}, {document_id})
          """).on("node_id" -> nodeId, "document_id" -> documentId).executeInsert()

      documentId
    }
    
    trait NodesAndDocuments extends DbTestContext {
      lazy val documentSet = insertDocumentSet
      lazy val nodeIds = setupNodes(documentSet) // must access nodeIds in tests to insert them in Databas

      lazy val documentIds = nodeIds.flatMap { n =>
        for (_ <- 1 to 2) yield insertDocument(n, documentSet)
      }      
    }
    
    "load document data for specified nodes with no other constraints" in new NodesAndDocuments {
      val selectedNodes = nodeIds.take(2)
      val expectedDocumentIds = documentIds.take(4)
      
      val persistentDocumentListDataLoader =
        new PersistentDocumentListDataLoader(selectedNodes, Nil)

      val documentData = persistentDocumentListDataLoader.loadDocumentSlice(0, 6)
      val loadedIds = documentData.map(_._1)
      
      loadedIds must haveTheSameElementsAs(expectedDocumentIds)
      documentData must have(_._2 == "title")
      documentData must have(_._3 == "textUrl")
      documentData must have(_._4 == "viewUrl")
    }

         
    "load slice of selected documents" in new NodesAndDocuments {
      val expectedDocumentIds = documentIds.slice(2, 5)
      
      val persistentDocumentListDataLoader =
        new PersistentDocumentListDataLoader(nodeIds, Nil)

      val documentData = persistentDocumentListDataLoader.loadDocumentSlice(2, 3)
      val loadedIds = documentData.map(_._1)
      
      loadedIds must haveTheSameElementsAs(expectedDocumentIds)     
      
    }

    "load document data for specified document ids with no other constraints" in new NodesAndDocuments {
      val selectedDocuments = documentIds.take(3)
      
      val persistentDocumentListDataLoader =
        new PersistentDocumentListDataLoader(Nil, selectedDocuments)

      val documentData = persistentDocumentListDataLoader.loadDocumentSlice(0, 6)
      val loadedIds = documentData.map(_._1)
      
      loadedIds must haveTheSameElementsAs(documentIds.take(3))
    }
    
    "load intersection of documents specified by nodes and document ids" in new NodesAndDocuments {
      val selectedNodes = nodeIds.take(2)
      val selectedDocuments = documentIds.drop(1)
      
      val persistentDocumentListDataLoader =
        new PersistentDocumentListDataLoader(selectedNodes, selectedDocuments)

      val documentData = persistentDocumentListDataLoader.loadDocumentSlice(0, 6)
      val loadedIds = documentData.map(_._1)
      
      loadedIds must haveTheSameElementsAs(documentIds.slice(1, 4))
    }
  }

  step(stop)
}