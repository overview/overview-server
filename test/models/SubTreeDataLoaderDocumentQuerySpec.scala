package models

import anorm._

import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import org.squeryl.Session
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

class SubTreeDataLoaderDocumentQuerySpec extends Specification {
  
  step(start(FakeApplication()))
    
  "SubTreeDataLoader" should {
    
    trait DocumentsLoaded extends DbTestContext {
      lazy val documentSetId = insertDocumentSet
      lazy val nodeIds = insertNodes(documentSetId)
      val subTreeDataLoader = new SubTreeDataLoader()
    }

    def insertDocumentSet(implicit connection: Connection) : Long = {
      SQL("""
        INSERT INTO document_set (id, query)
        VALUES (nextval('document_set_seq'), 'SubTreeDataLoaderDocumentQuerySpec')
        """).executeInsert().getOrElse(throw new Exception("fail"))
    }
    
    def insertNodes(documentSetId: Long)(implicit connection: Connection) : List[Long] = {
      for (i <- 1 to 3) yield
        SQL("""
          INSERT INTO node (id, document_set_id, parent_id, description)
          VALUES (nextval('node_seq'), {document_set_id}, NULL, 'node')
          """).on('document_set_id -> documentSetId).executeInsert().getOrElse(-1l)
    } toList
    
    def insertDocuments(nodes : List[Long], numberOfDocuments : Int = 10)
                       (implicit connection : Connection) : List[Long] = {
      
      val ids = nodes.flatMap { n => 
        val documents = for (i <- 1 to numberOfDocuments) yield SQL(
            """
              INSERT INTO document(id, title, text_url, view_url) VALUES 
                (nextval('document_seq'), {title}, {textUrl}, {viewUrl})
            """
          ).on("title" -> ("title-" + i),
               "textUrl" -> ("textUrl-" + i),
               "viewUrl" -> ("viewUrl-" + i)).executeInsert().getOrElse(-1l);
        
        documents.foreach(id =>
          SQL("INSERT INTO node_document(node_id, document_id) VALUES ({nodeId}, {documentId})").
          on("nodeId" -> n, "documentId" -> id).executeInsert())
          
        documents
      }
      ids
    }
    
	def documentIdsForNodes(nodes: List[Long], subTreeDataLoader : SubTreeDataLoader)(implicit connection: Connection) : List[Long] = {
	  val nodeDocumentIds = subTreeDataLoader.loadDocumentIds(nodes)
	  
	  nodeDocumentIds.map(_._3)
	}
    
    
	"return 10 document ids at most for a node" in new DocumentsLoaded {
      val documentIds = insertDocuments(nodeIds, 15)
      val loadedIds = documentIdsForNodes(nodeIds.take(1), subTreeDataLoader)
      
      loadedIds must haveTheSameElementsAs(documentIds.take(10))
    }
	
    "return all document ids if fewer than 10" in new DocumentsLoaded {
      val documentIds = insertDocuments(nodeIds, 5)
      val loadedIds = documentIdsForNodes(nodeIds, subTreeDataLoader)
      
      loadedIds must haveTheSameElementsAs(documentIds)
    } 
    
	"return document ids for several nodes" in new DocumentsLoaded {
	  val documentIds = insertDocuments(nodeIds)
	  val loadedIds = documentIdsForNodes(nodeIds, subTreeDataLoader)
	  
	  loadedIds must haveTheSameElementsAs(documentIds)
	}
	
	"handle nodes with no documents" in new DocumentsLoaded {
	  val documentIds = insertDocuments(nodeIds.take(2))
	  val loadedIds = documentIdsForNodes(nodeIds, subTreeDataLoader)
	  
	  loadedIds must haveTheSameElementsAs(documentIds.take(20))
	}
	
	"return empty list for unknown node Id" in new DocumentsLoaded {
	  val nodeDocumentIds = subTreeDataLoader.loadDocumentIds(List(1l))
	  
	  nodeDocumentIds must be empty
	}
	
	"returns total document count" in new DocumentsLoaded {
	  val numberOfDocuments = 15
	  val documentIds = insertDocuments(nodeIds.take(1), numberOfDocuments)

	  val nodeDocumentIds = subTreeDataLoader.loadDocumentIds(nodeIds.take(1))
	  
	  val documentCounts = nodeDocumentIds.map(_._2)
	  documentCounts.distinct must contain(numberOfDocuments.toLong).only
	}

	"return node ids" in new DocumentsLoaded {
	  val documentIds = insertDocuments(nodeIds)
	  
	  val nodeDocumentIds = subTreeDataLoader.loadDocumentIds(nodeIds)
	  
	  val nodes = nodeDocumentIds.map(_._1)
	  nodes.distinct must haveTheSameElementsAs(nodeIds)
	}
	
	"return all documents in nodes" in new DocumentsLoaded {
	  val numberOfDocuments = 3
	  val documentIds = insertDocuments(nodeIds, numberOfDocuments)
	  
	  val documents = subTreeDataLoader.loadDocuments(documentIds)
	  
	  documents must have size(nodeIds.size * 3)
	  documents.map(_._1) must haveTheSameElementsAs(documentIds)
	  
	  val titles = (1 to numberOfDocuments).map("title-" + _)
	  documents.map(_._2) must containAllOf(titles)
	  
	  val textUrls = (1 to numberOfDocuments).map("textUrl-" + _)
	  documents.map(_._3) must containAllOf(textUrls)
	  
	  val viewUrls = (1 to numberOfDocuments).map("viewUrl-" + _)
	  documents.map(_._4) must containAllOf(viewUrls)
	}
  }
  
  step(stop)
}
