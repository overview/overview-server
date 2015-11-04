package views.json.ImportJob

import play.api.i18n.Messages
import play.api.libs.json.{JsValue}
import play.api.libs.json.Json
import play.api.mvc.RequestHeader

import com.overviewdocs.models.ImportJob

object show {
  def apply(job: ImportJob)(implicit messages: Messages): JsValue = {
    Json.obj(
      "documentSetId" -> job.documentSetId,
      "progress" -> job.progress,
      "description" -> job.description.map(tuple => Messages(tuple._1, tuple._2: _*))
    )
  }
}
