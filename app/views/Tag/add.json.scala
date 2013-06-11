package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

object add {
  def apply(addedCount: Long) : JsValue = {
    toJson(Map("added" -> toJson(addedCount)))
  }
}
