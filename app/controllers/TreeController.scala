package controllers

import scala.collection.JavaConversions._
import scala.io
import scala.util.Random

import play.api.Play
import play.api.Play.current
import play.api.mvc._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json._
import controllers.TreeJsonWrapper._
import models.{Node,Tree}

object TreeController extends Controller {
	
	def temporarySetup() = Action {
	  val level1 = Seq(3)
	  val level2 = Seq(1, 3, 5)
	  val level3 = Seq(Seq(1), Seq(1, 1, 1), Seq(1, 1, 1, 1))
	  
	  val root = new Node()
	  root.description = "root"

	  val documentSet = new models.DocumentSet()
	  for (i <- 1 to 22) {
	    val document = new models.Document("document-" + i, "textUrl-" + i, "viewUrl-" + i)
	    documentSet.addDocument(document)
	    document.save
	  }

	  generateTreeLevel(root, documentSet.documents.toSeq, 4)
	  
	  val tree = new models.Tree()
	  tree.root = root
	  tree.save()
	  Ok(views.html.index.render(toJson(tree).toString))
	}
	
	def generateTreeLevel(root: Node, documents: Seq[models.Document], depth: Int) = {
	  
      documents.foreach(d => root.addDocument(d))

	  if ((depth > 1) && (documents.size > 1)) {
	    val numberOfChildren = Random.nextInt(scala.math.min(5, documents.size)) + 1
	    val children = generateChildren(numberOfChildren, documents, depth)
	    children.foreach(c => root.addChild(c))
	  }
	}

	def generateChildren(numberOfSiblings: Int, documents: Seq[models.Document], depth: Int) : Seq[Node] = {
	  val siblings = Seq[models.Node]()
	  if (numberOfSiblings > 0) {
	    val splitPoint = 
	      if (documents.size > numberOfSiblings) Random.nextInt(documents.size - numberOfSiblings) + 1 else 1 
	    
	    val (childDocuments, siblingDocuments) = documents.splitAt(splitPoint)
	    
	    val child = new Node()
	    child.description = "node height " + depth
	    generateTreeLevel(child, childDocuments, depth - 1)
	    
	    Seq[models.Node](child) ++ generateChildren(numberOfSiblings - 1, siblingDocuments, depth)
	  }
	  else {
		siblings
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
