package views.json.Tree

import play.api.libs.json.{JsValue, JsString}
import play.api.libs.json.Json

import models.orm.Tag
import org.overviewproject.tree.orm.{Node,Document,SearchResult}

object show {
  private[Tree] def writeNodeAndChildNodeIdsAndCounts(
    node: Node,
    childNodeIds: Iterable[Long],
    tagCounts: Iterable[(Long,Long)],
    searchResultCounts: Iterable[(Long,Long)]) : JsValue = {

    val jsonTagCounts = tagCounts.map({ x: (Long,Long) => (x._1.toString, x._2) }).toMap
    val jsonSearchResultCounts = searchResultCounts.map({ x: (Long,Long) => (x._1.toString, x._2) }).toMap

    Json.obj(
      "id" -> node.id,
      "description" -> node.description,
      "children" -> childNodeIds.toSeq,
      "doclist" -> Json.obj(
        "n" -> node.cachedSize,
        "docids" -> node.cachedDocumentIds
      ),
      "tagcounts" -> jsonTagCounts,
      "searchResultCounts" -> jsonSearchResultCounts
    )
  }

  private[Tree] def writeDocumentAndNodeIdsAndTagIds(document: Document, nodeIds: Iterable[Long], tagIds: Iterable[Long]) : JsValue = {
    Json.obj(
      "id" -> document.id,
      "description" -> document.description,
      "title" -> document.title,
      "nodeids" -> nodeIds,
      "tagids" -> tagIds
    )
  }

  private[Tree] def writeTagAndCount(tag: Tag, count: Long) : JsValue = {
    Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> ("#" + tag.color),
      "doclist" -> Json.obj(
        "n" -> count,
        "docids" -> Seq[Long]()
      )
    )
  }

  def apply(
    nodes: Iterable[(Node,Iterable[Long],Iterable[(Long,Long)],Iterable[(Long,Long)])],
    documents: Iterable[(Document,Iterable[Long],Iterable[Long])],
    tags: Iterable[(Tag,Long)],
    searchResults: Iterable[SearchResult]) : JsValue = {

    Json.obj(
      "nodes" -> nodes.map(Function.tupled(writeNodeAndChildNodeIdsAndCounts)),
      "documents" -> documents.map(Function.tupled(writeDocumentAndNodeIdsAndTagIds)),
      "searchResults" -> searchResults.map(views.json.SearchResult.show(_)),
      "tags" -> tags.map(Function.tupled(writeTagAndCount))
    )
  }
}
