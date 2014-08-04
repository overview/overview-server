package views.json.api.Document

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.tree.orm.Document

object index {
  def apply(documents: Seq[Document]): JsValue = {
    val jsons: Seq[JsValue] = documents.map(show(_))
    Json.toJson(jsons)
  }
}
