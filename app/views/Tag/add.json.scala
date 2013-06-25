package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json

object add {
  def apply(addedCount: Long) : JsValue = {
    Json.obj("added" -> addedCount)
  }
}
