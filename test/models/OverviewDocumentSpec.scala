package models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start,stop}
import play.api.test.FakeApplication

// OverviewDocument wraps models.orm.Document. Let's be transparent about that
// in this test.
import models.orm.{Document,DocumentType}
import helpers.DbTestContext

class OverviewDocumentSpec extends Specification {
  step(start(FakeApplication()))

  "OverviewDocument" should {
    trait OneDocument {
      def ormDocument : Document
      lazy val document = OverviewDocument(ormDocument)

      lazy val urlWithSimplePattern = document.url("https://localhost/{0}")
    }

    trait CsvImportDocumentScope extends Scope with OneDocument {
      override def ormDocument = Document(new DocumentType("CsvImportDocument"))
      lazy val csvImportDocument = document.asInstanceOf[OverviewDocument.CsvImportDocument]
    }

    trait DocumentCloudDocumentScope extends Scope with OneDocument {
      override def ormDocument = Document(new DocumentType("DocumentCloudDocument"))
      lazy val documentCloudDocument = document.asInstanceOf[OverviewDocument.DocumentCloudDocument]
    }

    "wrap a CsvImportDocument" in new CsvImportDocumentScope {
      document must beAnInstanceOf[OverviewDocument.CsvImportDocument]
    }

    "wrap a DocumentCloudDocument" in new DocumentCloudDocumentScope {
      document must beAnInstanceOf[OverviewDocument.DocumentCloudDocument]
    }

    "give the proper url for a CsvImportDocument with a url" in new CsvImportDocumentScope {
      override def ormDocument = super.ormDocument.copy(url = Some("https://example.org/foo"))
      urlWithSimplePattern must beEqualTo("https://example.org/foo")
    }

    "give the proper url for a CsvImprotDocument with no url" in new CsvImportDocumentScope {
      override def ormDocument = super.ormDocument.copy(id = 4L)
      urlWithSimplePattern must beEqualTo("https://localhost/4")
    }

    "give the proper url for a DocumentCloudDocument" in new DocumentCloudDocumentScope {
      override def ormDocument = super.ormDocument.copy(documentcloudId=Some("foobar"))
      urlWithSimplePattern must beEqualTo("https://www.documentcloud.org/documents/foobar")
    }
  }

  step(stop)
}
