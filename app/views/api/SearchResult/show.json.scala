package views.json.api.SearchResult

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.{JsValue,Json}

import org.overviewproject.tree.orm.SearchResult

object show {
  private val dateFormatter = ISODateTimeFormat.dateTime()
  private def formatDate(date: java.sql.Timestamp) = dateFormatter.print(new DateTime(date))

  def apply(searchResult: SearchResult): JsValue = Json.obj(
    "query" -> searchResult.query,
    "state" -> searchResult.state.toString,
    "createdAt" -> formatDate(searchResult.createdAt)
  )
}
