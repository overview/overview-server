package views.json.api

import play.api.libs.json.{JsValue,Json}

object error {
  def apply(message: String): JsValue = Json.obj(
    "message" -> message
  )
}
