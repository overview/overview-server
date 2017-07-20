package views.json.api.helpers

import java.sql.Timestamp
import org.joda.time.{DateTime,DateTimeZone}
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.{JsString,Writes}

trait JsonDateFormatter {
  implicit object TimestampWrites extends Writes[Timestamp] {
    private val formatter = ISODateTimeFormat.dateTime()
    private def format(t: java.sql.Timestamp) = formatter.print(new DateTime(t, DateTimeZone.UTC))

    def writes(t: Timestamp) = JsString(format(t))
  }
}
