package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import models.orm.Tag

object create {
  def apply(tag: Tag): JsValue = {
    toJson(Map(
      "id" -> toJson(tag.id),
      "name" -> toJson(tag.name),
      "color" -> toJson("#" + tag.color.getOrElse("7f7f7f"))
    ))
  }
}


