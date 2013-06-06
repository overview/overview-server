package views.json.SearchResult

import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import org.overviewproject.tree.orm.SearchResult

object show {
  def apply(searchResult: SearchResult) : JsValue = {
    toJson(Map(
      "id" -> toJson(searchResult.id),
      "query" -> toJson(searchResult.query),
      "state" -> toJson(searchResult.state.toString),
      "documents" -> toJson(Seq[Long]()) // TODO add document IDs
    ))
  }
}
