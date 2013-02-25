package views.json.DocumentSetUser

import models.orm.DocumentSetUser
import play.api.libs.json.Json.toJson
import play.api.libs.json.JsValue


object showUsers {
  import views.json.helper.ModelJsonConverters.JsonDocumentSetUser
  
  def apply(documentSetUsers: Iterable[DocumentSetUser]): JsValue = {
        toJson(Map(
        "viewers" -> toJson(documentSetUsers)
        ))
  }
}