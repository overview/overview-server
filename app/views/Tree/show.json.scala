package views.json.Tree

import play.api.libs.json.{JsValue, Writes}
import play.api.libs.json.Json.toJson

import models.PersistentTagInfo
import models.core.{Document, DocumentIdList, Node}
import models.orm.Tag
import views.json.helper.ModelJsonConverters._
import org.overviewproject.tree.orm.SearchResult

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

  private[Tree] def writeTagAndCount(tag: Tag, count: Long) : JsValue = {
    toJson(Map(
      "id" -> toJson(tag.id),
      "name" -> toJson(tag.name),
      "color" -> toJson(tag.color.map("#" + _).getOrElse("#666666")),
      "doclist" -> toJson(Map("n" -> toJson(count), "docids" -> toJson(Seq[Long]())))
    ))
  }

  def apply(nodes: Seq[Node], documents: Seq[Document], tags: Iterable[(Tag,Long)], searchResults: Iterable[SearchResult]) : JsValue = {
    toJson(
      Map(
        "nodes" -> toJson(nodes),
        "documents" -> toJson(documents),
        "searchResults" -> toJson(searchResults.map(views.json.SearchResult.show(_))),
        "tags" -> toJson(tags.map(Function.tupled(writeTagAndCount)))
      )
    )
  }
}
