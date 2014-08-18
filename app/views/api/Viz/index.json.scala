package views.json.api.Viz

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.models.Viz

object index {
  def apply(vizs: Seq[Viz]) : JsValue = {
    val jsons: Seq[JsValue] = vizs.map(show(_))
    Json.toJson(jsons)
  }
}
