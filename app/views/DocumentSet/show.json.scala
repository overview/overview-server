package views.json.DocumentSet

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState.NotStarted
import models.{ OverviewDocumentSet, OverviewDocumentSetCreationJob, OverviewUser }

object show {
  private def documentSetToJson(user: OverviewUser, documentSet: OverviewDocumentSet)(implicit lang: Lang): JsValue = {
    val documentSetMap = Map(
      "id" -> toJson(documentSet.id),
      "html" -> toJson(views.html.DocumentSet._documentSet(documentSet, user).toString)
    )

    toJson(documentSetMap)
  }

  def apply(user: OverviewUser, documentSet: OverviewDocumentSet): JsValue = {
    documentSetToJson(user, documentSet)
  }
}
