package views.json.Tree

import models.core.{Document, DocumentIdList, Node}
import play.api.libs.json.{JsValue, Writes}
import play.api.libs.json.Json.toJson


object show {
  private[Tree] implicit object JsonDocumentIdList extends Writes[DocumentIdList] {
    override def writes(documentIdList: DocumentIdList) : JsValue = {
      toJson(Map(
          "docids" -> toJson(documentIdList.firstIds),
          "n" -> toJson(documentIdList.totalCount)
      ))
    }
  }
  
  private[Tree] implicit object JsonNode extends Writes[Node] {
    override def writes(node: Node) : JsValue = {
      toJson(Map(
          "id" -> toJson(node.id),
          "description" -> toJson(node.description),
          "children" -> toJson(node.childNodeIds),
          "doclist" -> toJson(node.documentIds),
          "tagcounts" -> toJson(node.tagCounts)
      ))
    }
  }

  
  private[Tree] implicit object JsonDocument extends Writes[Document] {
    override def writes(document: Document) : JsValue = {
      toJson(Map(
        "id" -> toJson(document.id),
        "description" -> toJson(document.title)
      ))
    }
  }

  def apply(nodes: Seq[Node], documents: Seq[Document]) : JsValue = {
    toJson(
      Map(
        "nodes" -> toJson(nodes),
        "documents" -> toJson(documents),
        "tags" -> toJson(Seq[String]())
      )
    )
  }
}
