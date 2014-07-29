package views.json.api.SearchResult

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.tree.orm.SearchResult

object index {
  def apply(searchResults: Seq[SearchResult]): JsValue = {
    val jsons: Seq[JsValue] = searchResults.map(show(_))
    Json.toJson(jsons)
  }
}
