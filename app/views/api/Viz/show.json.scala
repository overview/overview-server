package views.json.api.Viz

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.models.Viz

object show extends views.json.api.helpers.JsonDateFormatter {
  def apply(viz: Viz): JsValue = Json.obj(
    "id" -> viz.id,
    "url" -> viz.url,
    "apiToken" -> viz.apiToken,
    "title" -> viz.title,
    "createdAt" -> viz.createdAt,
    "json" -> viz.json
  )
}
