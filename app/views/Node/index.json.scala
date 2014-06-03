package views.json.Node

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.libs.json.{Json, JsValue}

import org.overviewproject.tree.orm.{DocumentSetCreationJob,Node,SearchResult,Tag}
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

  def apply(
    vizs: Iterable[Viz],
    vizJobs: Iterable[DocumentSetCreationJob],
    nodes: Iterable[Node],
    tags: Iterable[Tag],
    searchResults: Iterable[SearchResult]) : JsValue = {

    Json.obj(
      "vizs" -> views.json.Viz.index(vizs, vizJobs),
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
