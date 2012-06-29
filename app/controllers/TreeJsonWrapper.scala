package controllers

import scala.collection.JavaConversions._
import play.api.libs.json._
import play.api.libs.json.Json._
import scala.collection.mutable.HashSet
import NodeJsonWrapper._
import models.{Document, PartiallyLoadedNode, Tree}

object TreeJsonWrapper {

  implicit object TreeWrites extends Writes[Tree] {
    
    def writes(tree: Tree) : JsValue = {
      val includedNodes = tree.root.getNodesBreadthFirst(12).toSeq
      val includedDocumentIds  =  HashSet[Document]()

      for (node <- includedNodes) {
        val partialNode = new PartiallyLoadedNode(node.getId)
        
        for (document <- partialNode.getDocuments(0, 10)) {
          includedDocumentIds.add(document)
        }
      }
     
      JsObject(Seq(
    		  "nodes" -> JsArray( includedNodes.map(n => toJson(n))),
    		  "documents" -> JsArray(
    				 includedDocumentIds.toSeq.map(d =>
    				   JsObject(
    				       Seq(
    				           "id" -> JsNumber(d.id.longValue),
    				           "description" -> JsString(d.getTitle)
    				           )
    				   )
    				 )
    		      ),
    		  "tags" -> JsArray(Seq())
    		 )
      )
    }
  }
}