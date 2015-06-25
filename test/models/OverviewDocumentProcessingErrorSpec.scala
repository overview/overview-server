package models

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.DocumentProcessingError

class OverviewDocumentProcessingErrorSpec extends DbSpecification {
  "OverviewDocumentProcessingError" should {
    trait BaseScope extends DbScope {
      val documentSetId = factory.documentSet().id

      lazy val errorGroups: Seq[(String, Seq[OverviewDocumentProcessingError])] = DeprecatedDatabase.inTransaction {
        OverviewDocumentProcessingError.sortedByStatus(documentSetId)
      }
    }

    "with HTTP errors" should {
      trait HttpErrorsScope extends BaseScope {
        val statusCodes = Seq(400, 302, 400, 500, 403, 999)
        statusCodes.foreach(c => factory.documentProcessingError(
          documentSetId=documentSetId,
          statusCode=Some(c)
        ))
      }

      "return errors and reasons sorted by status code" in new HttpErrorsScope {
        errorGroups.length must beEqualTo(5)
        errorGroups.map(e => e._2.head.statusCode) must beSorted
        errorGroups.map(e => e._2.head.url) must beSorted
      }

      "map message for http status codes" in new HttpErrorsScope {
        errorGroups.map(_._1) must beEqualTo(Seq(
          "302 Found",
          "400 Bad Request",
          "403 Forbidden",
          "500 Internal Server Error",
          "999 Unknown Status"
        ))
      }
    }

    "return internal errors first in list" in new BaseScope {
      factory.documentProcessingError(documentSetId=documentSetId, statusCode=None)
      factory.documentProcessingError(documentSetId=documentSetId, statusCode=Some(400))
      val internalErrors = errorGroups.head
      internalErrors._2.length must beEqualTo(1)
      internalErrors._2.head.statusCode must beNone
    }

    "map message for internal errors" in new BaseScope {
      factory.documentProcessingError(documentSetId=documentSetId)
      val internalErrors = errorGroups.head
      internalErrors._1 must be equalTo ("Unable to process file")
    }
  }
}
