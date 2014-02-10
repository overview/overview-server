package views.json.Node

import play.api.libs.json.JsValue
import play.api.libs.json.Json

import org.overviewproject.tree.orm.{Node,SearchResult,Tag}

object index {
  private[Node] def writeNode(node: Node) : JsValue = {
    Json.obj(
      "id" -> node.id,
      "parentId" -> node.parentId,
      "description" -> node.description,
      "size" -> node.cachedSize,
      "isLeaf" -> node.isLeaf
    )
  }

  private[Node] def writeTag(tag: Tag) : JsValue = {
    Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> ("#" + tag.color)
    )
  }

  def apply(
    nodes: Iterable[Node],
    tags: Iterable[Tag],
    searchResults: Iterable[SearchResult]) : JsValue = {

    Json.obj(
      "nodes" -> nodes.map(writeNode),
      "searchResults" -> searchResults.map(views.json.SearchResult.show(_)),
      "tags" -> tags.map(writeTag)
    )
  }

  def apply(nodes: Iterable[Node]) : JsValue = {
    Json.obj(
      "nodes" -> nodes.map(writeNode)
    )
  }
}
