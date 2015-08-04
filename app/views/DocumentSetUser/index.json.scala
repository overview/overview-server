package views.json.DocumentSetUser

import play.api.libs.json.{JsValue,Json}

import com.overviewdocs.models.DocumentSetUser

object index {
  def apply(documentSetUsers: Iterable[DocumentSetUser]): JsValue = {
    val emails : Iterable[String] = documentSetUsers.map(_.userEmail)
    val emailJsons = emails.map({ email: String => Json.obj("email" -> email) })

    Json.arr(emailJsons)
  }
}
