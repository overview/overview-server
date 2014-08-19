package views.json.api.VizObject

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.models.VizObject

object show {
  def apply(vizObject: VizObject): JsValue = Json.obj(
    "id" -> vizObject.id,
    "indexedLong" -> vizObject.indexedLong,
    "indexedString" -> vizObject.indexedString,
    "json" -> vizObject.json
  )
}
