package controllers

import scala.collection.JavaConversions._

import java.io.StringWriter
import java.sql.{Connection,Timestamp}

import play.api.libs.json._
import play.api.data.{Form,FormError}
import play.api.data.Forms._
import play.api.mvc.{Action,Controller,Request,AnyContent}

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import au.com.bytecode.opencsv.CSVWriter

import org.squeryl.PrimitiveTypeMode._

import models.orm.{DocumentSet,LogEntry,User}
import models.orm.LogEntry.ImplicitHelper._

object LogEntryController extends BaseController {
  def index(id: Long, extension: String) = authorizedAction(userOwningDocumentSet(id))(user => authorizedIndex(user, id, extension)(_: Request[AnyContent], _: Connection))
  def createMany(id: Long) = authorizedAction(parse.json, userOwningDocumentSet(id))(user => authorizedCreateMany(user, id)(_: Request[JsValue], _: Connection))

  private[controllers] def authorizedIndex(user: User, documentSetId: Long, extension: String)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentSet = user.documentSets.where(ds => ds.id === documentSetId).headOption

    documentSet.map({ ds =>
      val logEntries = ds.orderedLogEntries.page(0, 5000).toSeq.withUsers

      extension match {
        case ".csv" => logEntriesToCsv(logEntries)
        case _ => Ok(views.html.LogEntry.index(ds, logEntries))
      }
    }).getOrElse(
      NotFound("Invalid document set ID")
    )
  }

  private[controllers] def authorizedCreateMany(user: User, documentSetId: Long)(implicit request: Request[JsValue], connection: Connection) = {
    request.body match {
      case jsArray: JsArray =>
        var ok = true

        for (jsValue <- jsArray.as[List[JsValue]]) {
          ok &&= verifyAndInsertLogEntry(documentSetId, user, jsValue)
        }

        if (ok) {
          Ok("added log entries")
        } else {
          connection.rollback()
          BadRequest(createManyJsonInstructions)
        }
      case _ => BadRequest(createManyJsonInstructions)
    }
  }

  val createManyJsonInstructions =
      "Request must be of type application/json and look like " +
      "'[{date: \"ISO8601-datetime\", component: \"component\", action: \"action\", details: \"details\"}, ...]'"

  lazy val isoDateTimeFormat = ISODateTimeFormat.dateTime()

  implicit def iso8601DateFormatter = new play.api.data.format.Formatter[Timestamp] {
    override val format = Some("format.iso8601_date", Nil)

    def bind(key: String, data: Map[String,String]) = {
      play.api.data.format.Formats.stringFormat.bind(key, data).right.flatMap { s =>
        val ret = scala.util.control.Exception.catching(classOf[IllegalArgumentException])
          .either(new Timestamp(isoDateTimeFormat.parseDateTime(s).getMillis()))
          .left.map(e => Seq(FormError(key, "error.iso8601_date", Nil)))

        ret
      }
    }

    def unbind(key: String, value: Timestamp) = {
      Map(key -> isoDateTimeFormat.print(new DateTime(value)))
    }
  }

  def logEntryForm(documentSetId: Long, user: User) = Form(
    mapping(
      "date" -> of[Timestamp],
      "component" -> nonEmptyText,
      "action" -> nonEmptyText,
      "details" -> optional(text)
    )
    ((date, component, action, details) => LogEntry(0L, documentSetId, user.id, date, component, action, details.getOrElse("")))
    ((le: LogEntry) => Some(le.date, le.component, le.action, Some(le.details)))
  )

  private def logEntriesToCsv(logEntries: Seq[LogEntry]) = {
    val stringWriter = new StringWriter()
    val csvWriter = new CSVWriter(stringWriter)
    csvWriter.writeNext(Array("date", "email", "component", "action", "details"))
    logEntries.foreach({ e =>
      csvWriter.writeNext(Array(isoDateTimeFormat.print(new DateTime(e.date)), e.user.email, e.component, e.action, e.details))
    })
    csvWriter.close()
    Ok(stringWriter.toString()).as("text/csv")
  }

  private def verifyAndInsertLogEntry(documentSetId: Long, user: User, jsValue: JsValue) : Boolean = {
    jsValueToMap(jsValue).map(m =>
      logEntryForm(documentSetId, user).bind(m).fold(
        formWithErrors => false,
        logEntry => { logEntry.save; true }
      )
    ).getOrElse(false)
  }

  private def jsValueToMap(jsValue: JsValue) : Option[Map[String,String]] = {
    jsValue match {
      case jsObject: JsObject =>
        Some(jsObject.fields.toMap.mapValues(_ match {
          case s: JsString => s.value
          case _ => ""
        }))
      case _ => None
    }
  }
}
