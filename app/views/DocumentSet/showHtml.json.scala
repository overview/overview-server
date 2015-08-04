package views.json.DocumentSet

import play.api.i18n.Messages
import play.api.libs.json.{Json,JsValue}
import play.api.mvc.RequestHeader

import models.User
import com.overviewdocs.models.DocumentSet

object showHtml {
  def apply(user: User, documentSet: DocumentSet, nViews: Int)(implicit messages: Messages, request: RequestHeader): JsValue = {
    Json.obj(
      "id" -> documentSet.id,
      "html" -> views.html.DocumentSet._documentSet(documentSet, nViews, user).toString
    )
  }
}
