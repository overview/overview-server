package views.json.Tag

import models.core.{Document, Tag}
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters.{JsonDocument, JsonTag}

object remove {

  def apply(tag: Tag, removedCount: Long, documents: Seq[Document]) : JsValue = {
    toJson(Map(
    		"num_removed" -> toJson(removedCount),
    		"tag" -> toJson(tag),
    		"documents" -> toJson(documents)
    ))
  }
}