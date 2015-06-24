package views.json.api

import play.api.libs.json.{JsValue,Json}

object error {
  def apply(code: String, message: String): JsValue = Json.obj(
    "code" -> code,
    "message" -> message
  )
}