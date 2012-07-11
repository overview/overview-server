package models

import play.api.Play.{start, stop}
import play.api.test.FakeApplication
import com.avaje.ebean.Ebean
import org.specs2.mutable._
import play.api.test.FakeApplication

class PartiallyLoadedNodeSpec extends Specification {
	
  "A PartiallyLoadedNode" should {
    trait DbContext extends BeforeAfter {
      
      def before = {
        val application = FakeApplication();
       
        start(application)
        Ebean.beginTransaction
      }
            
      def after = {
        Ebean.endTransaction
        stop()
      }
    }

    "return Node description" in new DbContext {
      val node = new Node()
      node.setDescription("a Description")
      node.save
      
      val partialNode = new PartiallyLoadedNode(node.getId())
      
      partialNode.getDescription() must beEqualTo(node.description)
      
    }
    
    "return first 5 documents sorted by title" in new DbContext {
     //Ebean.getServer("default").getAdminLogging().setDebugGeneratedSql(true);
      val node = new Node()
      node.setDescription("a Description")
      
      val documentSet = new DocumentSet()
      
      for (i <- 10 to 30) {
        val id = "[" + i + "]"
        val document = new Document("document" + id, "textUrl" + id, "viewUrl" + id)
        documentSet.addDocument(document)
        
        node.addDocument(document)
      }
      
      node.save
      
      val partialNode = new PartiallyLoadedNode(node.getId())
      partialNode.getDescription must beEqualTo("a Description")
      val documents : Seq[Document] = partialNode.getDocuments(0, 4)
      
      documents.length must beEqualTo(5)
      
      val sortedDocuments = documents.sortBy(_.getTitle)
      
      sortedDocuments.length must beEqualTo(5)

      documents must beEqualTo(sortedDocuments)
      
    }
  }
  
}