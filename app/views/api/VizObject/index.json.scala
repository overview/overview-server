package views.json.api.VizObject

import play.api.libs.json.{Json,JsValue}

import org.overviewproject.models.VizObject

object index {
  def apply(vizObjects: Seq[VizObject]): JsValue = {
    val jsons: Seq[JsValue] = vizObjects.map(show(_))
    Json.toJson(jsons)
  }
}
