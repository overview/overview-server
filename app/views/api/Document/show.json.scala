package views.json.api.Document

import play.api.libs.json.{JsArray,JsNumber,JsObject,JsString,JsValue}
import scala.collection.mutable.Buffer

import org.overviewproject.tree.orm.Document

object show {
  def apply(document: Document): JsValue = {
    val keywords: Seq[JsValue] = document.description
      .split(" ")
      .filter(_.nonEmpty)
      .map(JsString(_))

    val buf = Buffer[(String,JsValue)](
      "id" -> JsNumber(document.id),
      "title" -> JsString(document.title.getOrElse("")),
      "keywords" -> JsArray(keywords)
    )
    if (document.suppliedId.getOrElse("") != "") {
      buf += ("suppliedId" -> JsString(document.suppliedId.getOrElse("")))
    }
    if (document.url.getOrElse("") != "") {
      buf += ("url" -> JsString(document.url.getOrElse("")))
    }
    JsObject(buf)
  }
}
