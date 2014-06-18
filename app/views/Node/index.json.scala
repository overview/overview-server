package views.json.Node

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.libs.json.{Json, JsValue}

import org.overviewproject.tree.orm.Node

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

  def apply(nodes: Iterable[Node]) : JsValue = {
    Json.obj(
      "nodes" -> nodes.map(writeNode)
    )
  }
}
