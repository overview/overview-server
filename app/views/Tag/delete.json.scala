package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

object delete {

  def apply(id: Long, name: String): JsValue = {
    toJson(Map(
      "id" -> toJson(id),
      "name" -> toJson(name)
      ))
  }
}


