package views.json.api.Tag

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.tree.orm.Tag

object index {
  def apply(tags: Seq[Tag]): JsValue = {
    val jsons: Seq[JsValue] = tags.map(show(_))
    Json.toJson(jsons)
  }
}
