package views.json.SearchResult

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson

import org.overviewproject.tree.orm.SearchResult

object show {
  private val dateFormatter = ISODateTimeFormat.dateTime()

  private def formatDate(date: java.sql.Timestamp) = dateFormatter.print(new DateTime(date))

  def apply(searchResult: SearchResult) : JsValue = {
    toJson(Map(
      "id" -> toJson(searchResult.id),
      "query" -> toJson(searchResult.query),
      "createdAt" -> toJson(formatDate(searchResult.createdAt)),
      "state" -> toJson(searchResult.state.toString),
      "documents" -> toJson(Seq[Long]()) // TODO add document IDs
    ))
  }
}
