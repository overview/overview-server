package views.json.Tag

import models.PersistentTagInfo
import models.core.Document
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters.JsonPersistentTagInfo

object add {

  def apply(tag: PersistentTagInfo, addedCount: Long, documents: Seq[Document]) : JsValue = {
    toJson(Map(
        "num_added" -> toJson(addedCount),
        "tag" -> toJson(tag),
        "documents" -> toJson(Seq[String]()) // ends up at the same JSON. Screw types, we want to remove this.
        ))
  }
}
