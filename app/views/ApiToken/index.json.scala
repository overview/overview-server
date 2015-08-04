package views.json.ApiToken

import play.api.libs.json.{JsValue, Json}

import com.overviewdocs.models.ApiToken

object index {
  def apply(tokens: Seq[ApiToken]) : JsValue = {
    Json.toJson(tokens.map(show(_)))
  }
}
