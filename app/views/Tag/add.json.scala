package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

object add {

  def apply(tagId: Long, addedCount: Long, totalCount: Long) : JsValue = {
    toJson(Map(
        "id" -> tagId,
        "numAdded" -> addedCount,
        "numTotal" -> totalCount
        ))
  }
}