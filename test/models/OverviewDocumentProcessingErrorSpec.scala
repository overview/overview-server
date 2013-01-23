package models

import helpers.DbTestContext
import models.orm.Schema
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import org.overviewproject.tree.orm.DocumentProcessingError

class OverviewDocumentProcessingErrorSpec extends Specification {

  step(start(FakeApplication()))

  "OverviewDocumentProcessingError" should {

    trait HttpErrors extends DbTestContext {
      var documentSetId: Long = _
      val statusCodes = Seq(400, 302, 400, 500, 403, 999)
      def errors = statusCodes.map(c => DocumentProcessingError(documentSetId, "url-" + c, "message", Some(c), Some("header")))
      var errorGroups: Seq[(String, Seq[OverviewDocumentProcessingError])] = _

      override def setupWithDb = {
        documentSetId = insertDocumentSet("OverviewDocumentProcessingErrorSpec")
        Schema.documentProcessingErrors.insert(errors)
        errorGroups = OverviewDocumentProcessingError.sortedByStatus(documentSetId)
      }
    }

    trait InternalErrors extends HttpErrors {
      override def errors = DocumentProcessingError(documentSetId, "url", "exception thrown", None, None) +: super.errors
    }

    "return errors and reasons sorted by status code" in new HttpErrors {
      errorGroups must have size (statusCodes.groupBy(identity).size)
      errorGroups.map(e => e._2.head.statusCode) must beSorted
      errorGroups.map(e => e._2.head.url) must beSorted
    }

    "return internal errors first in list" in new InternalErrors {
      val internalErrors = errorGroups.head
      internalErrors._2 must have size (1)
      internalErrors._2.head.statusCode must beNone
    }

    "map message for internal errors" in new InternalErrors {
      val internalErrors = errorGroups.head
      internalErrors._1 must be equalTo ("Overview Internal Error")
    }

    "map message for http status codes" in new HttpErrors {
      val expectedMessages = Seq(
        "302 Found",
        "400 Bad Request",
        "403 Forbidden",
        "500 Internal Server Error",
        "999 Unknown Status")

      errorGroups.map(_._1) must be equalTo (expectedMessages)
    }
  }

  step(stop)
}