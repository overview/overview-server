package controllers

import scala.collection.JavaConversions._

import java.io.StringWriter
import java.sql.Connection

import com.avaje.ebean.{Ebean,TxRunnable,TxScope,TxType}

import play.api.libs.json._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action,Controller}

import org.joda.time.format.{DateTimeFormatter,ISODateTimeFormat}
import org.joda.time.DateTimeZone

import au.com.bytecode.opencsv.CSVWriter

import models.{DocumentSet,LogEntry}

object LogEntryController extends Controller {
    val createManyJsonInstructions =
        "Request must be of type application/json and look like " +
        "'[{date: \"ISO8601-datetime\", component: \"component\", action: \"action\", details: \"details\"}, ...]'"
    val isoDateTimeFormat = ISODateTimeFormat.dateTime()

    val logEntryForm = Form(tuple(
            "date" -> nonEmptyText,
            "component" -> nonEmptyText,
            "action" -> nonEmptyText,
            "details" -> optional(text)
        ))

    def index(documentSetId: Long, extension: String) = Action {
        val documentSet = DocumentSet.find.byId(documentSetId) // TODO: security

        if (documentSet == null) {
            NotFound("Invalid document set ID")
        } else {
            val logEntries = Ebean.find(classOf[LogEntry])
                .where().eq("document_set_id", documentSetId)
                .orderBy("date DESC")
                .setMaxRows(5000)
                .findList()

            extension match {
                case ".csv" => logEntriesToCsv(logEntries)
                case _ => Ok(views.html.LogEntry.index(documentSet, logEntries))
            }
        }
    }

    private def logEntriesToCsv(logEntries: Seq[LogEntry]) = {
        val stringWriter = new StringWriter()
        val csvWriter = new CSVWriter(stringWriter)
        csvWriter.writeNext(Array("date", "username", "component", "action", "details"))
        logEntries.foreach({ e =>
          csvWriter.writeNext(Array(e.getDate().toString(), e.getUsername(), e.getComponent(), e.getAction(), e.getDetails()))
        })
        csvWriter.close()
        Ok(stringWriter.toString()).as("text/csv")
    }

    def createMany(documentSetId: Long) = Action(parse.json) { request =>
        val documentSet = DocumentSet.find.byId(documentSetId) // TODO: security

        if (documentSet == null) {
            NotFound("Invalid document set ID " + documentSetId)
        } else {
            request.body match {
                case jsArray: JsArray =>
                    var ok = true

                    runInTransactionAndRollbackIfFalse(() => {
                        for (jsValue <- jsArray.as[List[JsValue]]) {
                            ok &&= verifyAndInsertLogEntry(documentSet, jsValue)
                        }

                        ok
                    })

                    if (ok) {
                        Ok("added log entries")
                    } else {
                        BadRequest(createManyJsonInstructions)
                    }
                case _ => BadRequest(createManyJsonInstructions)
            }
        }
    }

    private def verifyAndInsertLogEntry(documentSet: DocumentSet, jsValue: JsValue) : Boolean = {
        var ok = true

        jsValue match {
            case jsObject: JsObject =>
                val stringMap = jsObject.fields.toMap.mapValues(_ match {
                    case s: JsString => s.value
                    case _ => null
                })

                logEntryForm.bind(stringMap).fold(
                    formWithErrors => {
                        ok = false
                    }, value => {
                        val (dateString, component, action, details) = value

                        val logEntry = new LogEntry()
                        logEntry.setDocumentSet(documentSet)
                        logEntry.setUsername("test user")
                        logEntry.setComponent(component)
                        logEntry.setAction(action)
                        details.map(logEntry.setDetails(_))

                        try {
                            val dateTime = isoDateTimeFormat.parseDateTime(dateString)
                            logEntry.setDate(dateTime.toDateTime(DateTimeZone.UTC))
                        } catch {
                            case e: IllegalArgumentException =>
                                ok = false
                        }

                        if (ok) {
                            logEntry.save()
                        }
                    }
                )
            case _ =>
                ok = false
        }

        ok
    }

    private def runInTransactionAndRollbackIfFalse(callback: () => Boolean) = {
        // If we're in an existing transaction, add a savepoint and rollback
        // to it if needed. If we're not in a transaction, start one, do the
        // savepoint stuff, and then commit. If we rolled back to the savepoint,
        // the commit will be a no-op and the database will be unchanged.

        val existingTransaction = Ebean.currentTransaction()
        val transaction = if (existingTransaction != null) existingTransaction else Ebean.beginTransaction()

        val connection : java.sql.Connection = transaction.getConnection()

        val savepoint = connection.setSavepoint()

        if (callback()) {
            connection.releaseSavepoint(savepoint)
        } else {
            connection.rollback(savepoint)
        }

        if (existingTransaction == null) {
            Ebean.commitTransaction()
        }
    }
}
