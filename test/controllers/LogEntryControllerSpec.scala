package controllers

import java.sql.{Connection,Timestamp}
import org.joda.time.{DateTime,DateTimeZone}
import org.specs2.mutable._
import play.api.Play.{start,stop}
import play.api.libs.json._
import play.api.test.{FakeApplication,FakeHeaders,FakeRequest}
import play.api.test.Helpers._

import models.orm.{DocumentSet,LogEntry,User}

class LogEntryControllerSpec extends Specification {
  import helpers.DbTestContext

  step(start(FakeApplication()))

  trait OurDbContext extends DbTestContext {
    lazy val user = (User("email@example.org", "password")).save
    lazy val documentSet1 = user.createDocumentSet("foo")
    lazy val documentSet2 = user.createDocumentSet("bar")
  }

  "createMany()" should {
    trait AuthorizedCreateManyContext extends OurDbContext {
      val validJson = JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00"),
        "details" -> JsString("details")
      ))))

      def createRequest(json: JsValue) = {
        FakeRequest("GET", "/", FakeHeaders(Map("Content-Type" -> Seq("application/json"))), json)
      }

      def jsonToResponse(documentSetId: Long, json: JsValue)(implicit connection: java.sql.Connection) = {
        val request = createRequest(json)
        LogEntryController.authorizedCreateMany(user, documentSetId)(request, connection)
      }
    }

    "do nothing with empty JSON array" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, JsArray())
      status(result).must(equalTo(OK))
    }

    "return a 400 error when not given a JSON array" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, JsString("foo"))
      status(result).must(equalTo(BAD_REQUEST))
    }

    "return a 400 error when a JSON array element isn't a JSON object" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, JsArray(Seq(JsString("foo"))))
      status(result).must(equalTo(BAD_REQUEST))
    }

    "return a 400 error when date isn't ISO-8601" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03 11:51:03.320-04:00")
      )))))
      status(result).must(equalTo(BAD_REQUEST)) // There's no "T"
    }

    "return a 400 error when component isn't specified" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, JsArray(Seq(JsObject(Seq(
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00")
      )))))
      status(result).must(equalTo(BAD_REQUEST))
    }

    "return a 400 error when action isn't specified" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00")
      )))))
      status(result).must(equalTo(BAD_REQUEST))
    }

    "not crash if another (not details) property is specified" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00"),
        "baz" -> JsString("oops")
      )))))
    }

    "return OK with valid JSON" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, validJson)
      status(result).must(equalTo(OK))
    }

    "update the database with valid JSON" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, validJson)
      val logEntry = LogEntry.query.single
      logEntry.component.must(equalTo("foo"))
      logEntry.action.must(equalTo("bar"))
      logEntry.date.must(equalTo(new Timestamp((new DateTime(2012, 7, 3, 15, 51, 3, 320, DateTimeZone.UTC).getMillis))))
      logEntry.details.must(equalTo("details"))
    }

    "set the right DocumentSet on the LogEntry" in new AuthorizedCreateManyContext {
      val result = jsonToResponse(documentSet1.id, validJson)
      val logEntry = LogEntry.query.single
      logEntry.documentSetId.must(equalTo(documentSet1.id))
    }

    "add multiple rows to the database when multiple entries are given" in new AuthorizedCreateManyContext {
      val json = validJson ++ JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo2"),
        "action" -> JsString("bar2"),
        "date" -> JsString("2012-07-03T15:39:54.123-04:00")
      ))))

      val result = jsonToResponse(documentSet1.id, json)
      status(result).must(equalTo(OK))
      documentSet1.logEntries.toSeq.length.must(equalTo(2))
    }

    "not add any rows to the database when the first row is valid but subsequent ones are invalid" in new AuthorizedCreateManyContext {
      val json = validJson ++ JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("invalid")
      ))))

      val result = jsonToResponse(documentSet1.id, json)
      status(result).must(equalTo(BAD_REQUEST))
      documentSet1.logEntries.toSeq.length.must(equalTo(0))
    }
  }

  "index()" should {
    trait AuthorizedIndexTrait extends OurDbContext {
      def getResult(documentSetId: Long, extension: String = ".html") = {
        LogEntryController.authorizedIndex(user, documentSetId, extension)(FakeRequest(), connection)
      }

      def getValidResult(extension: String = ".html") = {
        getResult(documentSet1.id, extension)
      }

      def createLogEntry(documentSetId: Long, date: String, details: String) = {
        LogEntry(0L, documentSetId, user.id, Timestamp.valueOf(date), "a component", "an action", details).save
      }
    }

    "return 404 when there is no DocumentSet" in new AuthorizedIndexTrait {
      val result = getResult(-1)
      status(result).must(equalTo(NOT_FOUND))
    }

    "return 200 when there is a DocumentSet" in new AuthorizedIndexTrait {
      val result = getValidResult()
      status(result).must(equalTo(OK))
    }

    "return HTML data by default" in new AuthorizedIndexTrait {
      val result = getValidResult("")
      contentType(result).must(beSome("text/html"))
    }

    "return CSV data when called with CSV format" in new AuthorizedIndexTrait {
      val result = getValidResult(".csv")
      contentType(result).must(beSome("text/csv"))
    }

    "return CSV data that quotes a quotation mark" in new AuthorizedIndexTrait {
      // opencsv ALWAYS writes quotes, and its escape character is another quote.
      createLogEntry(documentSet1.id, "2012-07-04 13:20:46", "de't\"ails")
      val result = getValidResult(".csv")
      contentAsString(result).must(contain("\"de't\"\"ails\""))
    }

    "return CSV dates in ISO8601 format" in new AuthorizedIndexTrait {
      createLogEntry(documentSet1.id, "2012-07-04 13:20:46", "de't\"ails")
      val result = getValidResult(".csv")
      contentAsString(result).must(contain("2012-07-04T13:20:46.000")) // ignore timezone--it cancels out
    }

    "ignore log entries on another DocumentSet" in new AuthorizedIndexTrait {
      createLogEntry(documentSet1.id, "2012-07-04 13:20:46", "de't\"ails")
      val result = getResult(documentSet2.id, ".csv")
      contentAsString(result).must(not(contain("2012-07-04")))
    }

    "order in reverse chronological order" in new AuthorizedIndexTrait {
      createLogEntry(documentSet1.id, "2012-07-04 13:20:46", "de't\"ails")
      createLogEntry(documentSet1.id, "2012-07-05 13:20:46", "de't\"ails 2")
      val result = getValidResult(".csv")
      val s = contentAsString(result)
      s.indexOf("2012-07-05").must(be_<(s.indexOf("2012-07-04")))
    }

    "show log entries in the HTML" in new AuthorizedIndexTrait {
      createLogEntry(documentSet1.id, "2012-07-04 13:20:46", "de't\"ails")
      val result = getValidResult()
      contentAsString(result).must(contain("de't"))
    }
  }

  step(stop)
}
