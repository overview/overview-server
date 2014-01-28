package views.json.DocumentSet

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.RequestHeader
import models.OverviewUser
import org.overviewproject.tree.orm.DocumentSet

object show {
  def apply(user: OverviewUser, documentSet: DocumentSet, treeId: Long)(implicit lang: Lang, request: RequestHeader): JsValue = {
    toJson(Map(
      "id" -> toJson(documentSet.id),
      "html" -> toJson(views.html.DocumentSet._documentSet(documentSet, treeId, user).toString)
    ))
  }
}
