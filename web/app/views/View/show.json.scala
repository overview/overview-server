package views.json.View

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.libs.json.{JsNull,JsValue,Json}

import com.overviewdocs.models.View

object show {
  private val iso8601Format = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC)
  private def dateToISO8601(time: Date) : String = iso8601Format.print(time.getTime())

  def apply(view: View): JsValue = {
    Json.obj(
      "type" -> "view",
      "id" -> view.id,
      "title" -> view.title,
      "url" -> view.url,
      "serverUrlFromPlugin" -> view.serverUrlFromPlugin,
      "apiToken" -> view.apiToken,
      "filter" -> view.viewFilter.map(_.json),
      "documentDetailLink" -> (view.documentDetailLink match {
        case None => JsNull
        case Some(link) => Json.obj(
          "url" -> link.url,
          "title" -> link.title,
          "text" -> link.text,
          "iconClass" -> link.iconClass
        )
      }),
      "createdAt" -> dateToISO8601(view.createdAt),
      "creationData" -> Json.obj()
    )
  }
}
