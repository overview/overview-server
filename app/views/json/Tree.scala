package views.json

import models.core.{Document, DocumentIdList, Node}
import play.api.libs.json.JsValue
import play.api.libs.json.{JsObject, Writes}
import play.api.libs.json.Json.toJson


object ATree {

  implicit object JsonDocumentIdList extends Writes[DocumentIdList] {
    def writes(documentIdList: DocumentIdList) : JsValue = {
      toJson(Map(
          "docids" -> toJson(documentIdList.firstIds),
          "n" -> toJson(documentIdList.totalCount)
      ))
    }
  }
  
  implicit object JsonNode extends Writes[Node] {
    def writes(node: Node) : JsValue = {
      toJson(Map(
          "id" -> toJson(node.id),
          "description" -> toJson(node.description),
          "children" -> toJson(node.childNodeIds),
          "doclist" -> toJson(node.documentIds)
      ))
    }
  }
  
  implicit object JsonDocument extends Writes[Document] {
    def writes(document: Document) : JsValue = {
      toJson(Map(
        "id" -> toJson(document.id),
        "description" -> toJson(document.title)
      ))
    }
  }
  

  def show(nodes: List[Node], documents: List[Document]) : JsValue = {
    toJson(
      Map(
        "nodes" -> toJson(nodes),
        "documents" -> toJson(documents),
        "tags" -> toJson(Seq[String]())
      )
    )
  }
}