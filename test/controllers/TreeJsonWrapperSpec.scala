package controllers

import scala.collection.JavaConversions._

import com.avaje.ebean.Ebean

import org.specs2.mutable._


import play.api.libs.json.Json._


import models.Node
import TreeJsonWrapper._


class TreeJsonWrapperSpec extends Specification {
  

  "The generated JSON for Tree" should {

    trait DbContext extends BeforeAfter {
      def before = Ebean.beginTransaction
            
      def after = Ebean.endTransaction
    }
    
    
    "contain the nodes" in new DbContext {
    	val tree = new models.Tree()

    	val root = new Node()

    	for (i <- 10 to 12) {
    	  val level1ChildNode = new Node()
    	  root.children.add(level1ChildNode)
    	  
    	  for (j <- 1 to 2) {
    	    val level2ChildNode = new Node()
    	  	level1ChildNode.children.add(level2ChildNode)
    	  }
    	}
    	
    	tree.root = root
    	tree.save
    	val treeJson = toJson(tree)
    	treeJson.toString must /("nodes") */("id" -> root.id)
    	
    	for (level1Node <- root.children.toSeq) {
    	  treeJson.toString must /("nodes") */("id" -> level1Node.id)
    	  for (level2Node <- level1Node.children.toSeq) {
    	    treeJson.toString must /("nodes") */("id" -> level2Node.id)
    	  }
    	}


    }
    
    "return documents in nodes" in new DbContext {
      val documentSet = new models.DocumentSet()
      
      val tree = new models.Tree()
      
      val root = new Node()
      
      for (i <- 1 to 20) {
        val document = new models.Document(documentSet, "document[" + i + "]", "textUrl-" + i, "viewUrl-" + i)
        documentSet.documents.add(document)
        
        root.addDocument(document)
      }

      for (i <- 0 to 2) {
      	val child = new Node()
      	documentSet.documents.slice(7 * i, 7 * i + 7).foreach{child.addDocument}
      	root.addChild(child)
      }
      
      tree.root = root
      tree.save

      val treeJson = toJson(tree)

      for (document <- documentSet.documents) {
        treeJson.toString must /("documents") */("id" -> document.id)
        treeJson.toString must /("documents") */("description" -> document.title)
    	  treeJson.toString.indexOf(document.title) must be equalTo(
    	      treeJson.toString.lastIndexOf(document.title))
      }
    }
    
    
    "fake Tags list" in new DbContext {

    	val tree = new models.Tree()

    	val root = new Node()
    	tree.root = root;
    	
    	tree.save
    	
    	val treeJson = toJson(tree)

    	treeJson.toString must contain ("tags")
    }


  }
  
 


}