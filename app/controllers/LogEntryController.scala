package controllers

import scala.collection.JavaConversions._

import java.io.StringWriter
import java.sql.Timestamp

import play.api.libs.json._
import play.api.data.{Form,FormError}
import play.api.data.Forms._
import play.api.mvc.{Action,BodyParsers,Controller,Request,AnyContent}

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

import au.com.bytecode.opencsv.CSVWriter

import org.squeryl.PrimitiveTypeMode._

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import models.{OverviewDatabase,OverviewUser}
import models.orm.{DocumentSet,LogEntry}
import models.orm.LogEntry.ImplicitHelper._

trait LogEntryController extends BaseController {
  def findOrmDocumentSetById(id: Long): Option[DocumentSet]

  def index(id: Long, extension: String) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    findOrmDocumentSetById(id).map({ documentSet =>
      val logEntries = documentSet.orderedLogEntries.page(0, 5000).toSeq.withUsers

      extension match {
        case ".csv" => logEntriesToCsv(logEntries)
        case _ => Ok(views.html.LogEntry.index(request.user, documentSet, logEntries))
      }
    }).getOrElse(
      NotFound("Invalid document set ID")
    )
  }

  def createMany(id: Long) = AuthorizedAction(BodyParsers.parse.tolerantJson, userOwningDocumentSet(id)) { implicit request =>
    request.body match {
      case jsArray: JsArray =>
        var ok = true

        for (jsValue <- jsArray.as[List[JsValue]]) {
          ok &&= verifyAndInsertLogEntry(id, request.user, jsValue)
        }

        if (ok) {
          Ok("added log entries")
        } else {
          OverviewDatabase.currentConnection.rollback()
          BadRequest(createManyJsonInstructions)
        }
      case _ => BadRequest(createManyJsonInstructions)
    }
  }

  private val createManyJsonInstructions =
      "Request must be of type application/json and look like " +
      "'[{date: \"ISO8601-datetime\", component: \"component\", action: \"action\", details: \"details\"}, ...]'"

  private lazy val isoDateTimeFormat = ISODateTimeFormat.dateTime()

  private implicit def iso8601DateFormatter = new play.api.data.format.Formatter[Timestamp] {
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

  private def logEntryForm(documentSetId: Long, user: OverviewUser) = Form(
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

  private def verifyAndInsertLogEntry(documentSetId: Long, user: OverviewUser, jsValue: JsValue) : Boolean = {
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

object LogEntryController extends LogEntryController {
  def findOrmDocumentSetById(id: Long) = DocumentSet.findById(id)
}
