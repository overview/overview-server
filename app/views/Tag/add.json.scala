package views.json.Tag

import models.PersistentTagInfo
import models.core.Document
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters.JsonPersistentTagInfo

object add {
  def apply(addedCount: Long) : JsValue = {
    toJson(Map("added" -> toJson(addedCount)))
  }
}
