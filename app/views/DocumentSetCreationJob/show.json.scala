package views.json.DocumentSetCreationJob

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import org.overviewproject.tree.orm.DocumentSetCreationJob
import models.orm.DocumentSet

object show {
  def apply(job: DocumentSetCreationJob, documentSet: DocumentSet, nAheadInQueue: Long)(implicit lang: Lang) : JsValue = {
    toJson(Map(
      "id" -> toJson(documentSet.id),
      "html" -> toJson(views.html.DocumentSetCreationJob._documentSetCreationJob(job, documentSet, nAheadInQueue).toString)
    ))
  }
}
