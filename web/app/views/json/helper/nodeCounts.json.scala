package views.json.helper

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

object nodeCounts {
  def apply(nodeCounts: Iterable[(Long, Int)]) : JsValue = {
    toJson(nodeCounts.flatMap(n => Seq(n._1, n._2)))
  }
}
