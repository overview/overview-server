package views.json.api.MassUpload

import play.api.libs.json.Json

import com.overviewdocs.models.GroupedFileUpload

object show {
  def apply(upload: GroupedFileUpload) = Json.obj(
    "guid" -> upload.guid.toString,
    "name" -> upload.name,
    "total" -> upload.size,
    "loaded" -> upload.uploadedSize
  )
}
