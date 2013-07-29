package views.json.Node

import play.api.libs.json.JsValue
import play.api.libs.json.Json

import org.overviewproject.tree.orm.Node

object show {
  def apply(node: Node) : JsValue = {
    Json.obj(
      "node" -> Json.obj(
        "id" -> node.id,
        "description" -> node.description
      )
    )
  }
}
