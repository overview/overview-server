package views.json.ImportJob

import play.api.i18n.Messages
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.RequestHeader

import com.overviewdocs.models.ImportJob

object index {
  def apply(jobs: Iterable[ImportJob])(implicit messages: Messages): JsValue = {
    val items : Iterable[JsValue] = jobs.map(show.apply _)
    toJson(items)
  }
}
