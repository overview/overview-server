package views.json.ImportJob

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.RequestHeader

import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob }


object index {
  def apply(tuples: Iterable[(DocumentSetCreationJob, DocumentSet, Long)])(implicit lang: Lang, request: RequestHeader) : JsValue = {
    val items : Iterable[JsValue] = tuples.map(Function.tupled(show.apply))
    toJson(items)
  }
}
