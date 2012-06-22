package controllers

import scala.collection.JavaConversions._
import scala.math.min

import play.api.libs.json._

import models.Node


object NodeJsonWrapper {
  
  implicit object NodeWrites extends Writes[Node] {

	val maxElementsInList = 10
	
    def writes(node: Node) : JsValue =
      JsObject(Seq(
    		  "id" -> JsNumber(node.id.longValue),
    		  "description" -> JsString(node.description),
    		  "children" -> JsArray(node.children.toSeq.map(n => JsNumber(n.id.longValue))),
    		  "doclist" -> JsObject(Seq(
    		      "docids" -> JsArray(node.documents.toSeq.take(maxElementsInList).
    		          map(d => JsNumber(d.id.longValue))),
    		      "n" -> JsNumber(min(maxElementsInList, node.documents.size()).intValue())
    		  )),
    		  "taglist" -> JsObject(Seq(
    		      "full" -> JsArray(Seq()),
    		      "partial" -> JsArray(Seq())
    		  ))
      ))
  }

}