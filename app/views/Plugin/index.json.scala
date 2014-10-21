package views.json.Plugin

import play.api.libs.json.{JsArray,JsValue}

import org.overviewproject.models.Plugin

object index {
  def apply(plugins: Seq[Plugin]): JsValue = {
    val jsons: Seq[JsValue] = plugins.map(show(_))
    JsArray(jsons)
  }
}
