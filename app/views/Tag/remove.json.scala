package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

object remove {

  def apply(tagId: Long, removedCount: Long, totalCount: Long) : JsValue = {
    toJson(Map(
    		"id" -> tagId,
    		"numRemoved" -> removedCount,
    		"numTotal" -> totalCount
    ))
  }
}