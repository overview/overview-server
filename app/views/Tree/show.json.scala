package views.json.Tree

import models.PersistentTagInfo
import models.core.{Document, DocumentIdList, Node}
import play.api.libs.json.{JsValue, Writes}
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters._


object show {
  
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

  def apply(nodes: Seq[Node], documents: Seq[Document], tags: Seq[PersistentTagInfo]) : JsValue = {
    toJson(
      Map(
        "nodes" -> toJson(nodes),
        "documents" -> toJson(documents),
        "searchResults" -> toJson(Seq[Long]()),
        "tags" -> toJson(tags)
      )
    )
  }
}
