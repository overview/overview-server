package controllers

import scala.collection.JavaConversions._

import play.api.libs.json._
import play.api.libs.json.Json._

import NodeJsonWrapper._

object TreeJsonWrapper {

  implicit object TreeWrites extends Writes[models.Tree] {
    
    def writes(tree: models.Tree) : JsValue = 
      JsObject(Seq(
    		  "nodes" -> JsArray( tree.root.getNodesBreadthFirst(12).toSeq.
    		      map(n => toJson(n))),
    		  "documents" -> JsArray(Seq()),
    		  "tags" -> JsArray(Seq())
    		      
      ))
    
  }
}