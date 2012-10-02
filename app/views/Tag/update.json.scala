package views.json.Tag

import models.core.Tag
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters.JsonTag

object update {

  def apply(tag: Tag): JsValue = toJson(tag)

}
