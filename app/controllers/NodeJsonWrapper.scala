package controllers

import scala.collection.JavaConversions._
import scala.math.min
import play.api.libs.json._
import models.Node
import models.PartiallyLoadedNode


object NodeJsonWrapper {
  
  implicit object NodeWrites extends Writes[Node] {

	val maxElementsInList = 10
	
    def writes(node: Node) : JsValue = {
      val partialNode = new PartiallyLoadedNode(node.getId)
      val partialDocumentList = partialNode.getDocuments(0, maxElementsInList - 1)
      
      JsObject(Seq(
    		  "id" -> JsNumber(node.id.longValue),
    		  "description" -> JsString(node.getDescription),
    		  "children" -> JsArray(node.getChildren.toSeq.map(n => JsNumber(n.id.longValue))),
    		  "doclist" -> JsObject(Seq(
    		      "docids" -> JsArray(partialDocumentList.
    		          map(d => JsNumber(d.getId.longValue))),
    		      "n" -> JsNumber(node.getDocuments.size.intValue())
    		  )),
    		  "taglist" -> JsObject(Seq(
    		      "full" -> JsArray(Seq()),
    		      "partial" -> JsArray(Seq())
    		  ))
      ))
	}
  }

}