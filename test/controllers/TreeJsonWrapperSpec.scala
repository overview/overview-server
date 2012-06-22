package controllers

import scala.collection.JavaConversions._

import org.specs2.mutable._
import org.specs2.specification._
import play.api.libs.json.Json._
import models.Node
import TreeJsonWrapper._
import ch.qos.logback.classic.Level


class TreeJsonWrapperSpec extends Specification {
//  Commented out until Play 2.0.2 fixes bug with multiple Application
//  "The generated JSON for Tree" should {
//    "contain the nodes" in {
//    	val tree = new models.Tree()
//
//    	val root = new Node()
//    	root.id = 1
//    	for (i <- 10 to 12) {
//    	  val level1ChildNode = new Node()
//    	  level1ChildNode.id = i
//    	  root.children.add(level1ChildNode)
//    	  for (j <- 1 to 2) {
//    	    val level2ChildNode = new Node()
//    	  	level2ChildNode.id = 10 * i + j
//    	  	level1ChildNode.children.add(level2ChildNode)
//    	  }
//    	}
//    	
//    	tree.root = root
//    	
//    	val treeJson = toJson(tree)
//    	
//    	treeJson.toString must /("nodes") */("id" -> 1.0)
//    	
//    	for (level1Node <- root.children.toSeq) {
//    	  treeJson.toString must /("nodes") */("id" -> level1Node.id)
//    	  for (level2Node <- level1Node.children.toSeq) {
//    	    treeJson.toString must /("nodes") */("id" -> level2Node.id)
//    	  }
//    	}
//    	treeJson.toString must /("nodes") */("id" -> 101.0)
//
//    }
//    
//    "fakes Document and Tags list" in {
//    	val tree = new models.Tree()
//
//    	val root = new Node()
//    	root.id = 1
//
//    	tree.root = root;
//    	val treeJson = toJson(tree)
//    	
//    	treeJson.toString must contain ("documents")
//    	treeJson.toString must contain ("tags")
//    }
//  }

}