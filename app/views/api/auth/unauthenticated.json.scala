package views.json.api.auth

import play.api.libs.json.{Json,JsValue}

object unauthenticated {
  def apply(): JsValue = { 
    Json.obj(
      "message" -> """You must set an Authorization header of 'Basic #{base64encode("YOUR-DOCSET-TOKEN:x-auth-token")}', where YOUR-DOCSET-TOKEN is a valid Overview API token."""
    )
  }
}
