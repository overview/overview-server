package views.json.Tag

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.models.Tag

object create {
  def apply(tag: Tag): JsValue = Json.obj(
    "id" -> tag.id,
    "name" -> tag.name,
    "color" -> ("#" + tag.color)
  )
}
