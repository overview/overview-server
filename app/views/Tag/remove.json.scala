package views.json.Tag

import models.PersistentTagInfo
import models.core.Document
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters.JsonPersistentTagInfo

object remove {
  def apply(removedCount: Long) : JsValue = {
    toJson(Map("removed" -> toJson(removedCount)))
  }
}
