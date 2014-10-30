package views.json.View

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.libs.json.{JsValue,Json}

import org.overviewproject.models.View

object show {
  private val iso8601Format = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC)
  private def dateToISO8601(time: Date) : String = iso8601Format.print(time.getTime())

  def apply(view: View): JsValue = {
    Json.obj(
      "type" -> "view",
      "id" -> view.id,
      "title" -> view.title,
      "url" -> view.url,
      "apiToken" -> view.apiToken,
      "createdAt" -> dateToISO8601(view.createdAt),
      "creationData" -> Json.obj()
    )
  }
}
