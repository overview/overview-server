package views.json.ApiToken

import play.api.libs.json.{JsValue, Json}

import org.overviewproject.models.ApiToken

object index {
  def apply(tokens: Seq[ApiToken]) : JsValue = {
    Json.toJson(tokens.map(show(_)))
  }
}
