package controllers

import scala.collection.JavaConversions._
import scala.io

import play.api.Play
import play.api.Play.current
import play.api.mvc._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json._

import controllers.TreeJsonWrapper._
import models.Node

object Tree extends Controller {
	
	def temporarySetup() = Action {
	  val level1 = Seq(3)
	  val level2 = Seq(1, 3, 5)
	  val level3 = Seq(Seq(1), Seq(1, 1, 1), Seq(1, 1, 1, 1))
	  
	  val root = new Node()
	  root.description = "root"
	    
	  generateNextLevel(level1, Seq(root), 10)
	  generateNextLevel(level2, root.children.toSeq, 20)
	  for ((child, grandkids) <- root.children.zip(level3)) {
	    generateNextLevel(grandkids, child.children.toSeq, 30)
	  }
	  
	  val tree = new models.Tree()
	  tree.root = root
	  tree.save()
	  Ok(views.html.index.render(toJson(tree).toString))
	}
	
	def generateNextLevel(numberOfNodes: Seq[Int], roots: Seq[Node], prefixStart: Int) {
	  var prefix = prefixStart
	  for ((n, root) <- numberOfNodes.zip(roots)) {
	    for (i <- 1 to n) {
	      val child = new Node()
	      child.description = "Node " + prefix + i
	      root.addChild(child)
	    }
	    prefix += 1
	  }
	}
  
    def root(documentSetId: Long) = Action {
// Leaving this here in case the complete JSON from the file is needed in testing the UI      
//        val file = Play.application.getFile("conf/stub-tree-root.json")
//        val json = io.Source.fromFile(file).mkString
//      SimpleResult(
//            header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/json")),
//            body = Enumerator(json)
//        )
      val tree = models.Tree.find.all.get(0)
      val json = toJson(tree)
      Ok(json)

    }
}
