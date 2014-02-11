package views.json.DocumentSet

import play.api.i18n.Lang
import play.api.libs.json.{Json,JsValue}
import play.api.mvc.RequestHeader

import models.OverviewUser
import org.overviewproject.tree.orm.{DocumentSet, Tree}

object show {
  def apply(user: OverviewUser, documentSet: DocumentSet, trees: Seq[Tree])(implicit lang: Lang, request: RequestHeader): JsValue = {
    Json.obj(
      "id" -> documentSet.id,
      "html" -> views.html.DocumentSet._documentSet(documentSet, trees, user).toString
    )
  }
}
