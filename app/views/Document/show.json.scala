package views.json.Document

import play.api.libs.json.{JsValue,Json}

import com.overviewdocs.models.Document

object show {
  def apply(document: Document): JsValue = {
    val url: String = document.url.getOrElse("")

    Json.obj(
      "id" -> document.id,
      "description" -> document.keywords.mkString(" "),
      "title" -> document.title,
      "text" -> document.text,
      "metadata" -> document.metadataJson,
      "suppliedId" -> document.suppliedId,
      "url" -> url
    )
  }
}
