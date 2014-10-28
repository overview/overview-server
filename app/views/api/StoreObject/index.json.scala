package views.json.api.StoreObject

import play.api.libs.json.{Json,JsValue}

import org.overviewproject.models.StoreObject

object index {
  def apply(storeObjects: Seq[StoreObject]): JsValue = {
    val jsons: Seq[JsValue] = storeObjects.map(show(_))
    Json.toJson(jsons)
  }
}
