package views.json.ImportJob

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import org.overviewproject.tree.orm.DocumentSetCreationJob
import models.orm.DocumentSet

object index {
  def apply(tuples: Iterable[(DocumentSetCreationJob, DocumentSet, Long)])(implicit lang: Lang) : JsValue = {
    val items : Iterable[JsValue] = tuples.map(Function.tupled(show.apply))
    toJson(items)
  }
}
