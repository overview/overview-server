package controllers

import scala.collection.JavaConversions._

import play.api.libs.json._
import play.api.libs.json.Json._
import scala.collection.mutable.HashSet


import NodeJsonWrapper._

object TreeJsonWrapper {

  implicit object TreeWrites extends Writes[models.Tree] {
    
    def writes(tree: models.Tree) : JsValue = {
      val includedNodes = tree.root.getNodesBreadthFirst(12).toSeq
      val includedDocumentIds  =  HashSet[models.Document]()

      for (node <- includedNodes) {
        for (document <- node.documents) {
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
    				           "description" -> JsString(d.title)
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