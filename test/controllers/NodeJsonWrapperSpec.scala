package controllers

import org.specs2.mutable._
import org.specs2.specification._
import play.api.libs.json.Json
import play.api.libs.json.Json._

import models.Node
import models.Document

import controllers.NodeJsonWrapper._


class NodeJsonWrapperSpec extends Specification {


	"The generated JSON for Node" should {
	  
	  "contain the id" in {
	    val node = new Node()
	    node.id = 5
	    
	    val nodeJson = toJson(node)
	    
	    nodeJson.toString must / ("id" -> node.id)
	  }
	  
	  "contain the description" in {
	    val node = new Node()
	    node.id = 5
	    node.description = "This is my description"

	    val nodeJson = toJson(node)
	    
	    nodeJson.toString must / ("description" -> node.description)
	  }
	  
	  "contain children node Ids" in {
	    val rootNode = new Node()
	    rootNode.id = 5
	    for (i <- 1 to 3) {
	      val childNode = new Node() 
	      childNode.id = i 
	      rootNode.children.add(childNode)
	    }
	    
	    val nodeJson = toJson(rootNode)
	    
	    // I think Specs2 Json matcher should allow something like:
	    //nodeJson.toString must /("children") "/(1.0)"
	    // but I can't get it to work, so am using the fragile test below. - JK
	    nodeJson.toString must contain ("\"children\":[1,2,3]" )
	    
	  }
	  
	  "contains doclist with docIds and count" in {
	    val rootNode = new Node()
	    rootNode.id = 5
	    for (i <- 1 to 3) {
	      val childNode = new Node() 
	      childNode.id = i 
	      rootNode.children.add(childNode)
	    }
	    
	    for (i <- 10 to 15) {
	    	val document = new Document(null, "title", "textUrl", "viewUrl")
	    	document.id = i
	    	rootNode.documents.add(document)
	    }
	    val nodeJson = toJson(rootNode)

	    //nodeJson.toString must /("doclist") /("docids") /(10.0)
	    nodeJson.toString must contain ("\"docids\":[10,11,12,13,14,15]")
	    nodeJson.toString must /("doclist") /("n" -> 6)
	  }
	  
	  "doclist lists at most first 10 docids" in {
		val rootNode = new Node()
	    rootNode.id = 5
	    
	    for (i <- 1 to 25) {
	    	val document = new Document(null, "title", "textUrl", "viewUrl")
	    	document.id = i
	    	rootNode.documents.add(document)
	    }
	    val nodeJson = toJson(rootNode)
	    
	    nodeJson.toString must /("doclist") /("n" -> 10)
	  }
	  
	  "fakes the tags list" in {
	    val rootNode = new Node()
	    rootNode.id = 5
	    
	    val nodeJson = toJson(rootNode)
	    
	    nodeJson.toString must contain("\"taglist\":{\"full\":[],\"partial\":[]}")
	  }
	  
	}
}