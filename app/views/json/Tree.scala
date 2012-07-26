package views.json

import models.core.{DocumentIdList, Node}
import play.api.libs.json.JsValue
import play.api.libs.json.{JsObject, Writes}
import play.api.libs.json.Json.toJson


object ATree {

  implicit object JsonDocumentIdList extends Writes[DocumentIdList] {
    def writes(documentIdList: DocumentIdList) : JsValue = {
      toJson(
        Map(
          "docids" -> toJson(documentIdList.firstIds),
          "n" -> toJson(documentIdList.totalCount)
        )
      )
    }
  }
  
  implicit object JsonNode extends Writes[Node] {
    def writes(node: Node) : JsValue = {
      toJson(
        Map(
          "id" -> toJson(node.id),
          "description" -> toJson(node.description),
          "children" -> toJson(node.childNodeIds),
          "doclist" -> toJson(node.documentIds)
        )
      )
    }
  }
  

  def show(nodes: List[Node]) : JsValue = {
    toJson(
      Map(
        "nodes" -> toJson(nodes)
      )
    )
  }
}