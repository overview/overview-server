package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json

import models.orm.Tag

object create {
  def apply(tag: Tag): JsValue = {
    Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> ("#" + tag.color)
    )
  }
}


