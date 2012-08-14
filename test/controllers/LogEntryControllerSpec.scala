package controllers

import play.api.Play.{start, stop}
import play.api.test.FakeApplication

import java.sql.Connection

import org.specs2.mutable._
import org.specs2.execute.Result

import com.avaje.ebean.{Ebean,Transaction,TxRunnable,TxScope,TxType}
import com.avaje.ebean.{AdminLogging,LogLevel}

import play.api.libs.json._

import play.api.test._
import play.api.test.Helpers._

import org.joda.time.{DateTime,DateTimeZone}

import models.LogEntry

class LogEntryControllerSpec extends Specification {
  import helpers.DbContext

  trait OurDbContext extends DbContext {
    override def before = {
        super.before
        Ebean.createSqlUpdate("INSERT INTO document_set (id, query) VALUES (1, 'foo')").execute()
        Ebean.createSqlUpdate("INSERT INTO document_set (id, query) VALUES (2, 'bar')").execute()
    }
  }

  "createMany()" should {
    def createRequest(json: JsValue) = {
      FakeRequest("GET", "/", FakeHeaders(Map("Content-Type" -> Seq("application/json"))), json)
    }

    def jsonToResponse(documentSetId: Long, json: JsValue) = {
      val request = createRequest(json)
      val result = LogEntryController.createMany(documentSetId)(request)
      result
    }

    val validJson = JsArray(Seq(JsObject(Seq(
      "component" -> JsString("foo"),
      "action" -> JsString("bar"),
      "date" -> JsString("2012-07-03T11:51:03.320-04:00"),
      "details" -> JsString("details")
    ))))

    "return a 404 error when given an invalid DocumentSet" in new OurDbContext {
      val result = jsonToResponse(9999, JsArray())
      status(result).must(equalTo(NOT_FOUND))
    }

    "do nothing with empty JSON array" in new OurDbContext {
      val result = jsonToResponse(1, JsArray())
      status(result).must(equalTo(OK))
    }

    "return a 400 error when not given a JSON array" in new OurDbContext {
      val result = jsonToResponse(1, JsString("foo"))
      status(result).must(equalTo(BAD_REQUEST))
    }

    "return a 400 error when a JSON array element isn't a JSON object" in new OurDbContext {
      val result = jsonToResponse(1, JsArray(Seq(JsString("foo"))))
      status(result).must(equalTo(BAD_REQUEST))
    }

    "return a 400 error when date isn't ISO-8601" in new OurDbContext {
      val result = jsonToResponse(1, JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03 11:51:03.320-04:00")
      )))))
      status(result).must(equalTo(BAD_REQUEST)) // There's no "T"
    }

    "return a 400 error when component isn't specified" in new OurDbContext {
      val result = jsonToResponse(1, JsArray(Seq(JsObject(Seq(
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00")
      )))))
      status(result).must(equalTo(BAD_REQUEST))
    }

    "return a 400 error when action isn't specified" in new OurDbContext {
      val result = jsonToResponse(1, JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00")
      )))))
      status(result).must(equalTo(BAD_REQUEST))
    }

    "not crash if another (not details) property is specified" in new OurDbContext {
      val result = jsonToResponse(1, JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00"),
        "baz" -> JsString("oops")
      )))))
    }

    "return OK with valid JSON" in new OurDbContext {
      val result = jsonToResponse(1, validJson)
      status(result).must(equalTo(OK))
    }

    "update the database with valid JSON" in new OurDbContext {
      val result = jsonToResponse(1, validJson)
      val logEntry = Ebean.find(classOf[LogEntry]).findUnique()
      logEntry.component.must(equalTo("foo"))
      logEntry.action.must(equalTo("bar"))
      logEntry.date.isEqual(new DateTime(2012, 07, 03, 15, 51, 03, 320, DateTimeZone.UTC)).must(equalTo(true)) // added 4h and made it UTC
      logEntry.details.must(equalTo("details"))
    }

    "set the right DocumentSet on the LogEntry" in new OurDbContext {
      val result = jsonToResponse(1, validJson)
      val logEntry = Ebean.find(classOf[LogEntry]).findUnique()
      logEntry.documentSet.id.must(equalTo(1))
    }

    "add multiple rows to the database when multiple entries are given" in new OurDbContext {
      val json = validJson ++ JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo2"),
        "action" -> JsString("bar2"),
        "date" -> JsString("2012-07-03T15:39:54.123-04:00")
      ))))

      val result = jsonToResponse(1, json)
      status(result).must(equalTo(OK))
      Ebean.find(classOf[LogEntry]).findRowCount.must(equalTo(2))
    }

    "not add any rows to the database when the first row is valid but subsequent ones are invalid" in new OurDbContext {
      val json = validJson ++ JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("invalid")
      ))))

      val result = jsonToResponse(1, json)
      status(result).must(equalTo(BAD_REQUEST))
      Ebean.find(classOf[LogEntry]).findRowCount.must(equalTo(0))
    }
  }

  "index()" should {
    def getResult(documentSetId: Long, extension: String = ".html") = {
      LogEntryController.index(documentSetId, extension)(FakeRequest())
    }

    def createLogEntry(documentSetId: Long, date: String, details: String) = {
      val update = Ebean.createSqlUpdate("""
          INSERT INTO log_entry (document_set_id, username, date, component, action, details)
          VALUES (:document_set_id, 'a user', CAST(:date AS TIMESTAMP), 'a component', 'an action', :details)
          """)
      update.setParameter("document_set_id", documentSetId)
      update.setParameter("date", date)
      update.setParameter("details", details)
      update.execute()
    }

    "return 404 when there is no DocumentSet" in new OurDbContext {
      val result = getResult(9999)
      status(result).must(equalTo(NOT_FOUND))
    }

    "return 200 when there is a DocumentSet" in new OurDbContext {
      val result = getResult(1)
      status(result).must(equalTo(OK))
    }

    "return HTML data by default" in new OurDbContext {
      val result = getResult(1, "")
      contentType(result).must(beSome("text/html"))
    }

    "return CSV data when called with CSV format" in new OurDbContext {
      val result = getResult(1, ".csv")
      contentType(result).must(beSome("text/csv"))
    }

    "return CSV data that quotes a quotation mark" in new OurDbContext {
      // opencsv ALWAYS writes quotes, and its escape character is another quote.
      createLogEntry(1, "2012-07-04 13:20:46", "de't\"ails")
      val result = getResult(1, ".csv")
      contentAsString(result).must(contain("\"de't\"\"ails\""))
    }

    "return CSV dates in ISO8601 format" in new OurDbContext {
      createLogEntry(1, "2012-07-04 13:20:46", "de't\"ails")
      val result = getResult(1, ".csv")
      contentAsString(result).must(contain("2012-07-04T13:20:46.000")) // ignore timezone--it cancels out
    }

    "ignore log entries on another DocumentSet" in new OurDbContext {
      createLogEntry(1, "2012-07-04 13:20:46", "de't\"ails")
      val result = getResult(2, ".csv")
      contentAsString(result).must(not(contain("2012-07-04")))
    }

    "order in reverse chronological order" in new OurDbContext {
      createLogEntry(1, "2012-07-04 13:20:46", "de't\"ails")
      createLogEntry(1, "2012-07-05 13:20:46", "de't\"ails 2")
      val result = getResult(1, ".csv")
      val s = contentAsString(result)
      s.indexOf("2012-07-05").must(be_<(s.indexOf("2012-07-04")))
    }

    "show log entries in the HTML" in new OurDbContext {
      createLogEntry(1, "2012-07-04 13:20:46", "de't\"ails")
      val result = getResult(1, "")
      contentAsString(result).must(contain("de't"))
    }
  }
}
