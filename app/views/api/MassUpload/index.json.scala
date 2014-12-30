package views.json.api.MassUpload

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.models.GroupedFileUpload

object index {
  def apply(uploads: Seq[GroupedFileUpload]) = {
    val jsons: Seq[JsValue] = uploads.map(show(_))
    Json.arr(jsons)
  }
}
