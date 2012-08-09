package views.json.Tag

import models.core.{Document, Tag}
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters.{JsonDocument, JsonTag}

object add {

  def apply(tag: Tag, addedCount: Long, documents: Seq[Document]) : JsValue = {
    toJson(Map(
        "num_added" -> toJson(addedCount),
        "tag" -> toJson(tag),
        "documents" -> toJson(documents)
        ))
  }
}