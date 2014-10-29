package views.json.api.View

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.models.View

object show extends views.json.api.helpers.JsonDateFormatter {
  def apply(view: View): JsValue = Json.obj(
    "id" -> view.id,
    "url" -> view.url,
    "apiToken" -> view.apiToken,
    "title" -> view.title,
    "createdAt" -> view.createdAt
  )
}
