package views.json.api.Tag

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.models.Tag

object show {
  def apply(tag: Tag): JsValue = Json.obj(
    "id" -> tag.id, // https://www.pivotaltracker.com/story/show/98813572
    "name" -> tag.name,
    "color" -> ("#" + tag.color)
  )
}
