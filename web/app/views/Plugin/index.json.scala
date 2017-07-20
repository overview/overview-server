package views.json.Plugin

import play.api.libs.json.{JsArray,JsValue}

import com.overviewdocs.models.Plugin

object index {
  def apply(plugins: Seq[Plugin]): JsValue = {
    val jsons: Seq[JsValue] = plugins.map(show(_))
    JsArray(jsons)
  }
}
