package views.json.Document

import play.api.libs.json._

import models.OverviewDocument

object show {
  def apply(document: OverviewDocument): JsValue = {
    val values = scala.collection.mutable.Buffer.empty[(String,JsValue)]
    values += ("id" -> JsNumber(document.id))
    values += ("description" -> JsString(document.description))

    document.title.map(s => values += ("title" -> JsString(s)))
    document.text.map(s => values += ("text" -> JsString(s)))
    document.suppliedId.map(s => values += ("suppliedId" -> JsString(s)))
    document.url.map(s => values += ("url" -> JsString(s)))

    JsObject(values)
  }
}
