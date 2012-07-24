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
      lazy val nodeIds = insertNodes
    }
    
    def insertNodes(implicit connection: Connection) : List[Long] = {
      for (i <- 1 to 3) yield
        SQL("INSERT INTO node VALUES (nextval('node_seq'), 'node')").
    	  executeInsert().getOrElse(-1l)
    } toList
    
    def insertDocuments(nodes : List[Long], numberOfDocuments : Int = 10)
                       (implicit connection : Connection) : List[Long] = {
      
      val ids = nodes.flatMap { n => 
        val documents = for (i <- 1 to numberOfDocuments) yield SQL(
            """
              INSERT INTO document VALUES 
                (nextval('document_seq'), {title}, {textUrl}, {viewUrl})
            """
          ).on("title" -> ("title-" + i),
               "textUrl" -> ("textUrl-" + i),
               "viewUrl" -> ("viewUrl-" + i)).executeInsert().getOrElse(-1l);
        
        documents.foreach(id =>
          SQL("INSERT INTO node_document VALUES ({nodeId}, {documentId})").
          on("nodeId" -> n, "documentId" -> id).executeInsert())
          
        documents
      }
      ids
    }
    
	def documentIdsForNodes(nodes: List[Long])(implicit connection: Connection) : List[Long] = {
	  val subTreeDataLoader = new SubTreeDataLoader()
	  val nodeDocuments = subTreeDataLoader.loadDocumentIds(nodes)
	  
	  nodeDocuments.map(_._3)
	}
    
    
	"return 10 documents at most for a node" in new DocumentsLoaded {
      val documentIds = insertDocuments(nodeIds, 15)
      val loadedIds = documentIdsForNodes(nodeIds.take(1))
      
      loadedIds must haveTheSameElementsAs(documentIds.take(10))
    }
	
    "return all documents if fewer than 10" in new DocumentsLoaded {
      val documentIds = insertDocuments(nodeIds, 5)
      val loadedIds = documentIdsForNodes(nodeIds)
      
      loadedIds must haveTheSameElementsAs(documentIds)
    } 
    
	"return documents for several nodes" in new DocumentsLoaded {
	  val documentIds = insertDocuments(nodeIds)
	  val loadedIds = documentIdsForNodes(nodeIds)
	  
	  loadedIds must haveTheSameElementsAs(documentIds)
	}
	
	"handle nodes with no documents" in new DocumentsLoaded {
	  val documentIds = insertDocuments(nodeIds.take(2))
	  val loadedIds = documentIdsForNodes(nodeIds)
	  
	  loadedIds must haveTheSameElementsAs(documentIds.take(20))
	}
	
	"return empty list for unknown node Id" in new DocumentsLoaded {
      val subTreeDataLoader = new SubTreeDataLoader()
	  val nodeDocuments = subTreeDataLoader.loadDocumentIds(List(1l))
	  
	  nodeDocuments must be empty
	}
	
	"returns total document count" in new DocumentsLoaded {
	  val numberOfDocuments = 15
	  val documentIds = insertDocuments(nodeIds.take(1), numberOfDocuments)

	  val subTreeDataLoader = new SubTreeDataLoader()
	  val nodeDocuments = subTreeDataLoader.loadDocumentIds(nodeIds.take(1))
	  
	  val documentCounts = nodeDocuments.map(_._2)
	  documentCounts.distinct must contain(numberOfDocuments.toLong).only
	}

	"return node ids" in new DocumentsLoaded {
	  val documentIds = insertDocuments(nodeIds)
	  
	  val subTreeDataLoader = new SubTreeDataLoader()
	  val nodeDocuments = subTreeDataLoader.loadDocumentIds(nodeIds)
	  
	  val nodes = nodeDocuments.map(_._1)
	  nodes.distinct must haveTheSameElementsAs(nodeIds)
	}
  }
  
  step(stop)
}