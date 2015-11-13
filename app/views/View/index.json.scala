package views.json.View

import java.util.Date
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTimeZone
import play.api.i18n.Messages
import play.api.libs.json.{JsValue,JsArray}
import scala.collection.mutable.ArrayBuffer

import com.overviewdocs.models.{Tree,View}

object index {
  private val iso8601Format = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC)
  private def dateToISO8601(time: Date) : String = iso8601Format.print(time.getTime())

  def apply(trees: Iterable[Tree], _views: Iterable[View])(implicit messages: Messages): JsValue = {
    val values = trees.map(views.json.Tree.show.apply) ++ _views.map(show.apply)
    JsArray(values.toSeq)
  }
}
