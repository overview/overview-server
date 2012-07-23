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
    
	"return documents for one node only" in new DbTestContext {
      
      val nodeId = SQL(
        """
          insert into Node values (nextval('node_seq'), 'node');
        """
      ).executeInsert().getOrElse(-1l)
      
      val documentIds = for (i <- 1 to 10) yield
        SQL(
          """
            insert into Document values 
              (nextval('document_seq'), {title}, {textUrl}, {viewUrl})
          """
        ).on("title" -> ("title-" + i),
            "textUrl" -> ("textUrl-" + i),
            "viewUrl" -> ("viewUrl-" + i)).executeInsert().getOrElse(-1);


      documentIds.foreach(id =>
        SQL("insert into node_document values ({nodeId}, {documentId})").
          on("nodeId" -> nodeId, "documentId" -> id).executeInsert())
          
      val subTreeDataLoader = new SubTreeDataLoader()
      
      val nodeDocuments = subTreeDataLoader.loadDocumentIds(List(nodeId))
      
      nodeDocuments must have size(10)
      val loadedIds = nodeDocuments.map(_._3)
      
      loadedIds must haveTheSameElementsAs(documentIds)
    }
  }
  
  step(stop)
}