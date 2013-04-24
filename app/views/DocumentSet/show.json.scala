package views.json.DocumentSet

import play.api.i18n.Lang
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import models.orm.DocumentSet
import models.OverviewUser

object show {
  def apply(user: OverviewUser, documentSet: DocumentSet)(implicit lang: Lang): JsValue = {
    toJson(Map(
      "id" -> toJson(documentSet.id),
      "html" -> toJson(views.html.DocumentSet._documentSet(documentSet, user).toString)
    ))
  }
}
