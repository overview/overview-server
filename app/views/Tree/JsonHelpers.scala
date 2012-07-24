package views.json.Tree

import scala.collection.JavaConversions._

import scala.collection.mutable.HashSet

import models.{Node,PartiallyLoadedNode,Document}

import play.api.libs.json._

object JsonHelpers {
  
	def generateSubTreeJson(nodes: List[models.core.Node], documents: List[models.core.Document]) :
	  JsValue = {
	  JsObject(Seq(
            "nodes" -> generateJsonArray(nodes),
            "documents" -> generateJsonArray(documents)
	      )
      )
	}
	
	private def generateJsonArray[A](data: List[A]) : JsValue = {
	  JsArray(data.map(generateJson))
	}
	
	def generateJson[A](data: A) : JsValue = data match {
	  case models.core.Node(id, description, childNodeIds, documentList) => {
	    JsObject(Seq(
	    			"id" -> JsNumber(id),
	    			"description" -> JsString(description),
	    			"children" -> JsArray(childNodeIds.map(JsNumber(_))),
	    			"doclist" -> generateJson(documentList)
	    			)
	    		)
	  }
	  case models.core.DocumentIdList(ids, total) => {
	    JsObject(Seq(
	    			"docids" -> JsArray(ids.map(JsNumber(_))),
	    			"n" -> JsNumber(total)
	    			)
	    		)
	  }
	  case models.core.Document(id, title, textUrl, viewUrl) => {
	    JsObject(Seq(
	    			"id" -> JsNumber(id),
	    			"title" -> JsString(title)
	    			)
	    		)
	  }
	}
	
    val maxElementsInList = 10

    def subNodeToJsValue(node: Node) : JsValue = {
        val partialNode = new PartiallyLoadedNode(node.getId)
        val partialDocumentList = partialNode.getDocuments(0, maxElementsInList - 1)
        // TODO: move the above into app.models

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

    def rootNodeToJsValue(rootNode: Node) : JsValue = {
        val includedNodes = rootNode.getNodesBreadthFirst(12).toSeq
        
        val partiallyLoadedNodes = includedNodes.map(n => new PartiallyLoadedNode(n.getId))
        val documentsInNodes = partiallyLoadedNodes.flatMap(_.getDocuments(0, maxElementsInList))
        val includedDocumentIds = documentsInNodes.toSet
        

        JsObject(Seq(
            "nodes" -> JsArray(includedNodes.map(JsonHelpers.subNodeToJsValue)),
            "documents" -> JsArray(
                includedDocumentIds.toSeq.map(d =>
                    JsObject(
                        Seq(
                            "id" -> JsNumber(d.id.longValue),
                            "description" -> JsString(d.getTitle)
                        )
                    )
            )),
            "tags" -> JsArray(Seq())
        ))
    }
}
