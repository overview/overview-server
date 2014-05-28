package views.json.Node

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.libs.json.{Json, JsValue}

import org.overviewproject.tree.orm.{Node,SearchResult,Tag}
import org.overviewproject.models.Viz

object index {
  private val iso8601Format = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC)

  private def dateToISO8601(time: Date) : String = {
    iso8601Format.print(time.getTime())
  }

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

  private[Node] def writeViz(viz: Viz) : JsValue = {
    val creationData = viz.creationData.map((x: (String,String)) => Json.arr(x._1, x._2))

    Json.obj(
      "id" -> viz.id,
      "title" -> viz.title,
      "createdAt" -> dateToISO8601(viz.createdAt),
      "creationData" -> creationData.toSeq
    )
  }

  def apply(
    vizs: Iterable[Viz],
    nodes: Iterable[Node],
    tags: Iterable[Tag],
    searchResults: Iterable[SearchResult]) : JsValue = {

    Json.obj(
      "vizs" -> vizs.map(writeViz),
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
