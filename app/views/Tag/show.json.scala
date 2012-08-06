package views.json.Tag

import play.api.libs.json.{JsValue, Writes}
import play.api.libs.json.Json.toJson

object show {

  def apply(tagIdCount: (Long, Long)) : JsValue = {
    toJson(Map(
        "id" -> tagIdCount._1,
        "count" -> tagIdCount._2
        ))
  }
}