package views.json.api.DocumentHeader

import play.api.libs.json.{JsArray,JsNumber,JsObject,JsString,JsValue}
import scala.collection.mutable.Buffer

import org.overviewproject.models.DocumentHeader

object show {
  def apply(document: DocumentHeader, fields: Set[String]): JsValue = {
    val buf = Buffer[(String,JsValue)]("id" -> JsNumber(document.id))

    if (fields.contains("documentSetId")) {
      buf += ("documentSetId" -> JsNumber(document.documentSetId))
    }

    if (fields.contains("title")) {
      buf += ("title" -> JsString(document.title))
    }

    if (fields.contains("keywords")) {
      buf += ("keywords" -> JsArray(document.keywords.map(JsString(_))))
    }

    if (fields.contains("suppliedId") && document.suppliedId.nonEmpty) {
      buf += ("suppliedId" -> JsString(document.suppliedId))
    }

    if (fields.contains("url") && document.url.getOrElse("") != "") {
      buf += ("url" -> JsString(document.url.getOrElse("")))
    }

    if (fields.contains("pageNumber") && document.pageNumber.isDefined) {
      buf += ("pageNumber" -> JsNumber(document.pageNumber.getOrElse(-1).toInt))
    }

    if (fields.contains("text")) {
      buf += ("text" -> JsString(document.text))
    }

    JsObject(buf)
  }
}
