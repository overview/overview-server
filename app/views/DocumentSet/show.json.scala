package views.json.DocumentSet

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState.NotStarted
import models.{ OverviewDocumentSet, OverviewDocumentSetCreationJob }

object show {
  private def documentSetToJson(documentSet: OverviewDocumentSet)(implicit lang: Lang): JsValue = {
    val documentSetMap = Map(
      "id" -> toJson(documentSet.id),
      "html" -> toJson(views.html.DocumentSet._documentSet(documentSet, false).toString)
    )

    toJson(documentSetMap)
  }

  def apply(documentSet: OverviewDocumentSet): JsValue = {
    documentSetToJson(documentSet)
  }
}
