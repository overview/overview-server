package views.json.ImportJob

import play.api.i18n.Messages
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.RequestHeader

import com.overviewdocs.models.{ DocumentSet, DocumentSetCreationJob }

object index {
  def apply(tuples: Iterable[(DocumentSetCreationJob, DocumentSet, Int)])(implicit messages: Messages, request: RequestHeader) : JsValue = {
    val items : Iterable[JsValue] = tuples.map(Function.tupled(show.apply))
    toJson(items)
  }
}
