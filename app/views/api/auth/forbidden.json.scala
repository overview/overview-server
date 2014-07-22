package views.json.api.auth

import play.api.libs.json.{Json,JsValue}

object forbidden {
  def apply(): JsValue = { 
    Json.obj(
      "message" -> "Your API token is valid, but it does not grant you access to this action"
    )
  }
}
