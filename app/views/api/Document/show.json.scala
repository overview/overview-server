package views.json.api.Document

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.tree.orm.Document

object show {
  def apply(document: Document): JsValue = Json.obj(
    "title" -> document.title,
    "url" -> document.url
  )
}
