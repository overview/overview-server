package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

object remove {
  def apply(removedCount: Long) : JsValue = {
    toJson(Map("removed" -> toJson(removedCount)))
  }
}
