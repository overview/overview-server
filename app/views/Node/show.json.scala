package views.json.Node

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import org.overviewproject.tree.orm.Node

object show {
  def apply(node: Node) : JsValue = {
    toJson(Map(
      "node" -> toJson(Map(
        "id" -> toJson(node.id),
        "description" -> toJson(node.description)
      ))
    ))
  }
}
