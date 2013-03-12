package views.json.DocumentSetCreationJob

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import models.{OverviewDocumentSet, OverviewDocumentSetCreationJob}

object index {
  def apply(tuples: Iterable[(OverviewDocumentSetCreationJob, OverviewDocumentSet)])(implicit lang: Lang) : JsValue = {
    val items : Iterable[JsValue] = tuples.map { tuple : (OverviewDocumentSetCreationJob, OverviewDocumentSet) =>
      show(tuple._1, tuple._2)
    }
    toJson(items)
  }
}
