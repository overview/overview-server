package models

import scala.collection.JavaConversions._

import com.avaje.ebean.Ebean

import org.specs2.mutable.{Specification,BeforeAfter}

class SelectionSpec extends Specification {
  
  "Selections " should {
    
      trait DbContext extends BeforeAfter {

		def before = { 
		  Ebean.beginTransaction
		  createTree
		}
        def after = Ebean.endTransaction
      }
      
      def createTree() {
       	val documentSet = new DocumentSet()
    	val node = new Node()
      
    	documentSet.setQuery("foo")
      
    	for (i <- 10 to 99) {
    	  val document = new Document(documentSet, createTitle(i), "texturl", "viewurl")
    	  documentSet.getDocuments.add(document)
    	  node.addDocument(document)
    	}
      
    	val tree = new Tree()
    	tree.setRoot(node)
        
    	tree.save()
      }
  
      def createTitle(n: Long) = "document[" + n + "]"
	  
    "return all documents in tree, with no other constraints" in new DbContext {
      val tree = Tree.find.all().get(0)
      
      val selection =  new Selection(tree)

      val allDocuments = selection.slice(0, 89)
        
      allDocuments.size must beEqualTo(90)
    }
    
    "return all documents in tree, in sorted order" in new DbContext {
      val tree = Tree.find.all().get(0)
      
      val selection =  new Selection(tree)

      val allDocuments = selection.slice(0, 89)

      for (i <- 10 to 99) {
        allDocuments.get(i - 10).getTitle  must beEqualTo(createTitle(i))
      }
    }
    
    "return a slice of documents in the tree" in new DbContext {
      val tree = Tree.find.all().get(0)
      
      val selection =  new Selection(tree)

      val start = 15l;
      val end = 45l;
      
      val titleStart = start + 10
      val titleEnd = end + 10
      val titles = (titleStart to titleEnd).map(createTitle)
      
      val allDocuments = selection.slice(start, end)

      allDocuments.size must beEqualTo(end - start + 1)
      for ((d, t) <- allDocuments.zip(titles)) {
        d.getTitle must beEqualTo(t)
      }
      
    }
  }

}