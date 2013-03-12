package views.json.DocumentSetCreationJob

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import models.{OverviewDocumentSet, OverviewDocumentSetCreationJob}
import models.upload.OverviewUploadedFile
import views.ScopedMessages
import views.helper.DocumentSetHelper

object show {
  def apply(job: OverviewDocumentSetCreationJob, documentSet: OverviewDocumentSet)(implicit lang: Lang) : JsValue = {
    toJson(Map(
      "id" -> toJson(documentSet.id),
      "html" -> toJson(views.html.DocumentSetCreationJob._documentSetCreationJob(job, documentSet).toString)
    ))
  }
}
