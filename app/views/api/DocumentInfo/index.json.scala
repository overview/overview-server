package views.json.api.DocumentInfo

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.models.DocumentInfo

object index {
  def apply(documents: Seq[DocumentInfo]): JsValue = {
    val jsons: Seq[JsValue] = documents.map(show(_))
    Json.toJson(jsons)
  }
}
