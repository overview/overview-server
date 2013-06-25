package views.json.Tree

import play.api.libs.json.JsValue
import play.api.libs.json.Json

import org.overviewproject.tree.orm.{Node,SearchResult,Tag}

object show {
  private[Tree] def writeNodeAndChildNodeIds(node: Node, childNodeIds: Iterable[Long]) : JsValue = {
    Json.obj(
      "id" -> node.id,
      "description" -> node.description,
      "children" -> childNodeIds.toSeq,
      "size" -> node.cachedSize
    )
  }

  private[Tree] def writeTagAndCount(tag: Tag, count: Long) : JsValue = {
    Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> ("#" + tag.color),
      "size" -> count
    )
  }

  def apply(
    nodes: Iterable[(Node,Iterable[Long])],
    tags: Iterable[(Tag,Long)],
    searchResults: Iterable[SearchResult]) : JsValue = {

    Json.obj(
      "nodes" -> nodes.map(Function.tupled(writeNodeAndChildNodeIds)),
      "searchResults" -> searchResults.map(views.json.SearchResult.show(_)),
      "tags" -> tags.map(Function.tupled(writeTagAndCount))
    )
  }

  def apply(nodes: Iterable[(Node,Iterable[Long])]) : JsValue = {
    Json.obj(
      "nodes" -> nodes.map(Function.tupled(writeNodeAndChildNodeIds))
    )
  }
}
