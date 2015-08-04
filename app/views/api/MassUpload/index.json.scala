package views.json.api.MassUpload

import play.api.libs.json.{JsValue,Json}

import com.overviewdocs.models.GroupedFileUpload

object index {
  def apply(uploads: Seq[GroupedFileUpload]) = {
    val jsons: Seq[JsValue] = uploads.map(show(_))
    Json.arr(jsons)
  }
}
