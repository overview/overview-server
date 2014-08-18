package views.json.api.SearchResult

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.tree.orm.SearchResult

object show extends views.json.api.helpers.JsonDateFormatter {
  def apply(searchResult: SearchResult): JsValue = Json.obj(
    "query" -> searchResult.query,
    "state" -> searchResult.state.toString,
    "createdAt" -> searchResult.createdAt
  )
}
