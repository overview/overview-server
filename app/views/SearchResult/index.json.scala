package views.json.SearchResult

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import org.overviewproject.tree.orm.SearchResult

object index {
  def apply(searchResults: Iterable[SearchResult]) : JsValue = {
    toJson(searchResults.map(show(_)).toSeq)
  }
}
