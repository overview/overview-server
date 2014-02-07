package controllers

import au.com.bytecode.opencsv.CSVReader
import java.io.StringReader
import java.sql.Timestamp
import org.joda.time.{DateTime,DateTimeZone}
import org.joda.time.format.ISODateTimeFormat
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json._
import play.api.mvc.{ AnyContent, AnyContentAsJson, Request }
import play.api.Play.{ start, stop }
import play.api.test.{ FakeApplication, FakeHeaders, FakeRequest }
import play.api.test.Helpers._
import org.overviewproject.tree.orm.DocumentSet
import controllers.auth.AuthorizedRequest
import models.orm.{ User }
import org.overviewproject.tree.orm.LogEntry
import org.overviewproject.tree.orm.finders.ResultPage
import models.OverviewUser

class LogEntryControllerSpec extends Specification with Mockito {
  import helpers.DbTestContext

  step(start(FakeApplication()))

  trait BaseScope extends Scope {
    val mockStorage = mock[LogEntryController.Storage]
    val controller = new LogEntryController {
      override val storage = mockStorage
    }
    val user = mock[OverviewUser].smart
    val documentSetId = 1L
  }

  "createMany()" should {
    trait AuthorizedCreateManyContext extends BaseScope {
      val validJson : JsArray = JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00"),
        "details" -> JsString("details")
      ))))

      def json : JsValue = validJson
      def fakeRequest = FakeRequest("POST", "/", FakeHeaders(Seq("Content-Type" -> Seq("application/json"))), json)
      def request = new AuthorizedRequest(fakeRequest, user)

      lazy val response = controller.createMany(documentSetId)(request)
    }

    "do nothing with empty JSON array" in new AuthorizedCreateManyContext {
      override def json = JsArray()
      status(response) must beEqualTo(OK)
    }

    "return a 400 error when not given a JSON array" in new AuthorizedCreateManyContext {
      override def json = JsString("foo")
      status(response).must(equalTo(BAD_REQUEST))
    }

    "return a 400 error when a JSON array element isn't a JSON object" in new AuthorizedCreateManyContext {
      override def json = JsArray(Seq(JsString("foo")))
      status(response).must(equalTo(BAD_REQUEST))
    }

    "return a 400 error when date isn't ISO-8601" in new AuthorizedCreateManyContext {
      override def json = JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03 11:51:03.320-04:00")
      ))))
      status(response).must(equalTo(BAD_REQUEST)) // There's no "T"
    }

    "return a 400 error when component isn't specified" in new AuthorizedCreateManyContext {
      override def json = JsArray(Seq(JsObject(Seq(
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00")
      ))))
      status(response).must(equalTo(BAD_REQUEST))
    }

    "return a 400 error when action isn't specified" in new AuthorizedCreateManyContext {
      override def json = JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00")
      ))))
      status(response).must(equalTo(BAD_REQUEST))
    }

    "not crash if an extraneous property is specified" in new AuthorizedCreateManyContext {
      override def json = JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("2012-07-03T11:51:03.320-04:00"),
        "baz" -> JsString("oops")
      ))))
      status(response) must beEqualTo(OK)
    }

    "return OK with valid JSON" in new AuthorizedCreateManyContext {
      status(response).must(equalTo(OK))
    }

    "update the database with valid JSON" in new AuthorizedCreateManyContext {
      val expectedLogEntry = LogEntry(
        id=0L,
        documentSetId=documentSetId,
        userId=0L,
        component="foo",
        action="bar",
        details="details",
        date=new Timestamp((new DateTime(2012, 7, 3, 15, 51, 3, 320, DateTimeZone.UTC).getMillis)
      ))
      response
      there was one(mockStorage).insertLogEntries(argThat(beLike[Iterable[LogEntry]] {
        case arg =>
          arg.size must beEqualTo(1)
          arg.head.toString must beEqualTo(expectedLogEntry.toString)
      }))
    }

    "add multiple rows to the database when multiple entries are given" in new AuthorizedCreateManyContext {
      override def json = validJson ++ JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo2"),
        "action" -> JsString("bar2"),
        "date" -> JsString("2012-07-03T15:39:54.123-04:00")
      ))))

      response

      there was one(mockStorage).insertLogEntries(any)
    }

    "not add any rows to the database when the first row is valid but subsequent ones are invalid" in new AuthorizedCreateManyContext {
      override def json = validJson ++ JsArray(Seq(JsObject(Seq(
        "component" -> JsString("foo"),
        "action" -> JsString("bar"),
        "date" -> JsString("invalid")
      ))))

      response

      there was no(mockStorage).insertLogEntries(any)
    }
  }

  "index()" should {
    trait AuthorizedIndexTrait extends BaseScope {
      def extension = ".html"
      def documentSet : Option[DocumentSet] = Some(DocumentSet(id=documentSetId))
      def logEntries : Seq[(LogEntry,User)] = Seq()
      def fakeRequest = FakeRequest("GET", "/")
      def request = new AuthorizedRequest(fakeRequest, user)
      val baseLogEntry = LogEntry(documentSetId=documentSetId, userId=0L,date=new Timestamp(scala.compat.Platform.currentTime), component="component")
      val baseUser = User()

      mockStorage.findDocumentSet(documentSetId) returns documentSet
      mockStorage.findLogEntries(anyLong, anyInt, anyInt) returns ResultPage(logEntries, 1000, 1)
      lazy val response = controller.index(documentSetId, extension)(request)
    }

    "return 404 when there is no DocumentSet" in new AuthorizedIndexTrait {
      override def documentSet = None
      status(response).must(equalTo(NOT_FOUND))
    }

    "return 200 when there is a DocumentSet" in new AuthorizedIndexTrait {
      status(response).must(equalTo(OK))
    }

    "return HTML data by default" in new AuthorizedIndexTrait {
      override def extension = ""
      contentType(response).must(beSome("text/html"))
    }

    "return CSV data when called with CSV format" in new AuthorizedIndexTrait {
      override def extension = ".csv"
      contentType(response).must(beSome("text/csv"))
    }

    "return CSV data that quotes a quotation mark" in new AuthorizedIndexTrait {
      // opencsv ALWAYS writes quotes, and its escape character is another quote.
      override def logEntries = Seq((baseLogEntry.copy(details="de't\"ails"), baseUser))
      override def extension = ".csv"
      contentAsString(response).must(contain("\"de't\"\"ails\""))
    }

    "return CSV dates in ISO8601 format" in new AuthorizedIndexTrait {
      override def logEntries = Seq((baseLogEntry, baseUser))
      override def extension = ".csv"
      val csv = new CSVReader(new StringReader(contentAsString(response)))
      val row : Array[String] = csv.readAll.get(1)
      val dateString : String = row.head
      ISODateTimeFormat.dateTime.parseDateTime(dateString) must not(throwA[IllegalArgumentException])
    }

    "show log entries in the HTML" in new AuthorizedIndexTrait {
      override def logEntries = Seq((baseLogEntry.copy(details="de't\"ails"), baseUser))
      contentAsString(response).must(contain("de&#x27;t"))
    }
  }

  step(stop)
}
