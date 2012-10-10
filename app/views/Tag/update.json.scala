package views.json.Tag

import models.PersistentTagInfo
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters.JsonPersistentTagInfo

object update {

  def apply(tag: PersistentTagInfo): JsValue = toJson(tag)

}
