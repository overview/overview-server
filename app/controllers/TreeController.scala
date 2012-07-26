package controllers

import scala.collection.JavaConversions._
import scala.io
import scala.util.Random
import play.api.db.DB
import play.api.Play
import play.api.Play.current
import play.api.mvc._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json._
import models._
import anorm.SQL
import anorm.SqlParser.scalar

import views.json.Tree.JsonHelpers


object TreeController extends Controller {
	
	def temporarySetup() = Action {
	  val documentSet = new DocumentSet()

	  val root = new Node()
	  root.setDocumentSet(documentSet)
    root.setDescription("root")

	  for (i <- 1 to 2200) {
	    val document = new Document("document-" + i, "textUrl-" + i, "viewUrl-" + i)
	    documentSet.addDocument(document)
	    document.save
	  }

    documentSet.save
    root.save

	  generateTreeLevel(root, documentSet.documents.toSeq, 12)

	  Ok("Setup complete")
	}
	
	def generateTreeLevel(root: Node, documents: Seq[Document], depth: Int) = {
    documents.foreach(root.addDocument)

	  if ((depth > 1) && (documents.size > 1)) {
	    val numberOfChildren = Random.nextInt(scala.math.min(7, documents.size)) + 1
	    val children = generateChildren(root, numberOfChildren, documents, depth)
	    children.foreach(root.addChild)
	  }
	}

	def generateChildren(parent: Node, numberOfSiblings: Int, documents: Seq[Document], depth: Int) : Seq[Node] = {
    val child = new Node()
    child.setDocumentSet(parent.documentSet)
    child.setDescription("node height " + depth)

	  if (numberOfSiblings > 1) {
	    val splitPoint = 
	      if (documents.size > numberOfSiblings) Random.nextInt(documents.size - numberOfSiblings) + 1 else 1 
	    
	    val (childDocuments, siblingDocuments) = documents.splitAt(splitPoint)
	    
	    generateTreeLevel(child, childDocuments, depth - 1)
	    
	    Seq[Node](child) ++ generateChildren(parent, numberOfSiblings - 1, siblingDocuments, depth)
	  }
	  else {
      generateTreeLevel(child, documents, depth - 1)
      Seq[Node](child)
	  }
	}
	
    def root(id: Long) = Action {
      // FIXME handle security

      DB.withTransaction { implicit connection =>
        val rootId = SQL("""
          SELECT id FROM node
          WHERE document_set_id = {document_set_id} AND parent_id IS NULL
          """).on('document_set_id -> id).as(scalar[Long].single)

      	val subTreeLoader = new SubTreeLoader(rootId, 4)
      	val nodes = subTreeLoader.loadNodes
      	val documents = subTreeLoader.loadDocuments(nodes)

      	val json = JsonHelpers.generateSubTreeJson(nodes, documents)
        Ok(json)
      }
    }

    def node(id: Long, nodeId: Long) = Action {
      // FIXME handle security

      DB.withTransaction { implicit connection =>
      	val subTreeLoader = new SubTreeLoader(nodeId, 4)
      	val nodes = subTreeLoader.loadNodes
      	val documents = subTreeLoader.loadDocuments(nodes)

      	val json = JsonHelpers.generateSubTreeJson(nodes, documents)
        Ok(json)
      }
    }
}
