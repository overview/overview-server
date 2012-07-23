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
      val nodeId = SQL("INSERT INTO node VALUES (nextval('node_seq'), 'node')").
    		  executeInsert().getOrElse(-1l)
      
      List(nodeId)
    }
    
    def insertDocuments(nodes : List[Long])(implicit connection : Connection) :
      List[Long] = {
      
      val ids = nodes.flatMap { n => 
        val documents = for (i <- 1 to 10) yield SQL(
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
    
	"return documents for one node only" in new DocumentsLoaded {
      val documentIds = insertDocuments(nodeIds)
      val subTreeDataLoader = new SubTreeDataLoader()
      
      val nodeDocuments = subTreeDataLoader.loadDocumentIds(nodeIds)
      
      val loadedIds = nodeDocuments.map(_._3)
      
      loadedIds must haveTheSameElementsAs(documentIds)
    }
  }
  
  step(stop)
}