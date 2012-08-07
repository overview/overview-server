package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

object nodeCounts {
	
  def apply(nodeCounts: Seq[(Long, Long)]) : JsValue = {
    toJson(nodeCounts.flatMap(n => Seq(n._1, n._2)))
  }
}