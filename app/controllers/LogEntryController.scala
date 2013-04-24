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

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import models.{OverviewDatabase,OverviewUser,ResultPage}
import models.orm.{ DocumentSet, LogEntry, User }
import models.orm.finders.{ DocumentSetFinder, LogEntryFinder }
import models.orm.stores.LogEntryStore

trait LogEntryController extends Controller {
  trait Storage {
    def findDocumentSet(documentSetId: Long) : Option[DocumentSet]
    def findLogEntries(documentSetId: Long, pageSize: Int, page: Int) : ResultPage[(LogEntry,User)]

    def insertLogEntries(logEntries: Iterable[LogEntry]) : Unit
  }

  def index(id: Long, extension: String) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    storage.findDocumentSet(id) match {
      case Some(documentSet) =>
        val logEntries = storage.findLogEntries(documentSet.id, 5000, 1)

        extension match {
          case ".csv" => logEntriesToCsv(logEntries)
          case _ => Ok(views.html.LogEntry.index(request.user, documentSet, logEntries))
        }
      case None => NotFound("Invalid document set ID")
    }
  }

  def createMany(id: Long) = AuthorizedAction(BodyParsers.parse.tolerantJson, userOwningDocumentSet(id)) { implicit request =>
    request.body match {
      case jsArray: JsArray =>
        val parsed : Iterable[Either[String,LogEntry]] = jsArray.as[List[JsValue]].map(parseLogEntry(id, request.user, _))
        val (errorsAsEithers, entriesAsEithers) = parsed.partition(_.isLeft)

        if (errorsAsEithers.isEmpty) {
          val entries = entriesAsEithers.map(_.right.get)
          storage.insertLogEntries(entries)
          Ok("ok")
        } else {
          BadRequest(errorsAsEithers.head.left.get)
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

  private def logEntriesToCsv(logEntries: Iterable[(LogEntry,User)]) = {
    val stringWriter = new StringWriter()
    val csvWriter = new CSVWriter(stringWriter)
    csvWriter.writeNext(Array("date", "email", "component", "action", "details"))
    logEntries.foreach({ case (logEntry, user) =>
      csvWriter.writeNext(Array(
        isoDateTimeFormat.print(new DateTime(logEntry.date)),
        user.email,
        logEntry.component,
        logEntry.action,
        logEntry.details
      ))
    })
    csvWriter.close()
    Ok(stringWriter.toString()).as("text/csv")
  }

  private def parseLogEntry(documentSetId: Long, user: OverviewUser, jsValue: JsValue) : Either[String,LogEntry] = {
    logEntryForm(documentSetId, user).bind(jsValue).fold(
      formWithErrors => Left(formWithErrors.errors.toString),
      logEntry => Right(logEntry)
    )
  }

  protected val storage : LogEntryController.Storage
}

object LogEntryController extends LogEntryController {
  object DatabaseStorage extends Storage {
    override def findDocumentSet(documentSetId: Long) = {
      DocumentSetFinder.byDocumentSet(documentSetId).headOption
    }

    override def findLogEntries(documentSetId: Long, pageSize: Int, page: Int) = {
      val logEntries = LogEntryFinder.byDocumentSet(documentSetId).withUsers
      ResultPage(logEntries, pageSize, page)
    }

    override def insertLogEntries(logEntries: Iterable[LogEntry]) = {
      LogEntryStore.insertBatch(logEntries)
    }
  }

  override val storage = DatabaseStorage
}
