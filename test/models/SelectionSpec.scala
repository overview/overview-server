package models

import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import scala.collection.JavaConversions._

import com.avaje.ebean.Ebean

import org.specs2.mutable.{Specification,BeforeAfter}

import helpers.DbContext

class SelectionSpec extends Specification {
  
  "Selections " should {

    trait TreeCreatedContext extends DbContext {
        override def before = {  super.before ; createTree() }
    }
      
    def createTree() {
      val documentSet = new DocumentSet()
      documentSet.save()
      val node = new Node()
      node.setDocumentSet(documentSet)
      
      documentSet.setQuery("foo")
      
      for (i <- 10 to 99) {
        val document = new Document(createTitle(i), "texturl", "viewurl")
        documentSet.addDocument(document)
        node.addDocument(document)
      }
      
      val tree = new Tree()
      tree.setRoot(node)
        
      tree.save()
    }
  
    def createTitle(n: Int) = "document[" + n + "]"
	  
    "return all documents in tree, with no other constraints" in new TreeCreatedContext {
      val tree = Tree.find.all().get(0)
      val selection =  new Selection(tree)
      val allDocuments = selection.findDocumentsSlice(0, 91)
      
      allDocuments.size must beEqualTo(90)
    }
    
    "return all documents in tree, in sorted order" in new TreeCreatedContext {
      val tree = Tree.find.all().get(0)
      val selection =  new Selection(tree)
      val allDocuments = selection.findDocumentsSlice(0, 91).toSeq

      for (i <- 10 to 99) {
        allDocuments.get(i - 10).getTitle  must beEqualTo(createTitle(i))
      }
    }
    
    "return a slice of documents in the tree" in new TreeCreatedContext {
      val tree = Tree.find.all().get(0)
      val selection =  new Selection(tree)

      val start = 15;
      val end = 45;
      
      val titleStart = start + 10
      val titleEnd = end + 10
      val titles = (titleStart until titleEnd).map(createTitle)
      
      val allDocuments = selection.findDocumentsSlice(start, end)

      allDocuments.size must beEqualTo(end - start)
      for ((d, t) <- allDocuments.zip(titles)) {
        d.getTitle must beEqualTo(t)
      }
      
    }
    
    "return only documents under a node" in new TreeCreatedContext {
      addExtraChildrenToTree()
      val tree = Tree.find.all().get(0)
      val childNodes = tree.getRoot.getChildren
      
      val childNodeIds = childNodes.map(_.getId.longValue).toSet
      val selection =  new Selection(tree, nodeids = childNodeIds)
      val selectedDocuments = selection.findDocumentsSlice(0, 99)
      
      val allDocumentsInChildren = childNodes.map(_.getDocuments).flatten.toSeq
      
      selectedDocuments.size must beEqualTo(allDocumentsInChildren.size)
      
      for ((sd, cd) <- selectedDocuments.zip(allDocumentsInChildren.sortBy(_.getTitle))) {
        sd.getTitle must beEqualTo(cd.getTitle)
      }

    }
    
      
    "return document and node intersection in sorted order" in new TreeCreatedContext {
      addExtraChildrenToTree()
      val tree = Tree.find.all().get(0)
      val childNodes = tree.getRoot.getChildren

      val firstDocuments = childNodes.map(_.getDocuments.toSeq.get(0))
      val firstDocumentIds = firstDocuments.map(_.getId.longValue).toSet
      
      val childNodeIds = childNodes.map(_.getId.longValue).toSet

      val selection =  new Selection(tree, nodeids = childNodeIds,
    		  						       documentids = firstDocumentIds)
      
      val selectedDocuments = selection.findDocumentsSlice(0, 99)
      
      selectedDocuments.size must beEqualTo(firstDocuments.size)
      
      val sortedDocuments = firstDocuments.toSeq.sortBy(_.getTitle)

      for ((sd, cd) <- selectedDocuments.zip(sortedDocuments)) {
        sd.getTitle must beEqualTo(cd.getTitle)
      }
    
    }
  }

  def addExtraChildrenToTree() = {
    val documentSet = DocumentSet.find.all().get(0)
    val tree = Tree.find.all().get(0)

    val documentsInChild1 = tree.getRoot.getDocuments.toSeq.slice(20, 40)
    val documentsInChild2 = tree.getRoot.getDocuments.toSeq.slice(10, 15)
    val documentsInChild3 = tree.getRoot.getDocuments.toSeq.slice(50, 70)
      
    val documentsInChildren = Seq(documentsInChild1, 
    			                  documentsInChild2,
    		  					  documentsInChild3)
    val childNodes = Seq.fill(3) { val n = new Node; n.setDocumentSet(documentSet); n }

    for ((child, documents) <- childNodes.zip(documentsInChildren)) {
      documents.foreach(child.addDocument(_))
      tree.getRoot.addChild(child)
    }

    tree.update
      
  }

}
