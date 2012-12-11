package models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start,stop}
import play.api.test.FakeApplication

// OverviewDocument wraps models.orm.Document. Let's be transparent about that
// in this test.
import org.overviewproject.tree.orm.{ Document, DocumentType }

class OverviewDocumentSpec extends Specification {
  step(start(FakeApplication()))

  "OverviewDocument" should {
    trait OneDocument {
      def ormDocument: Document
      lazy val document = OverviewDocument(ormDocument)

      lazy val urlWithSimplePattern = document.url("https://localhost/{0}")
    }

    trait CsvImportDocumentScope extends Scope with OneDocument {
      def ormDocumentId : Long = 1L
      def suppliedUrl : Option[String] = Some("http://example.org")
      override def ormDocument = Document(new DocumentType("CsvImportDocument"), id=ormDocumentId, url=suppliedUrl)
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
      override def suppliedUrl = Some("https://example.org/foo")
      urlWithSimplePattern must beEqualTo("https://example.org/foo")
    }

    "give the proper url for a CsvImportDocument with no url" in new CsvImportDocumentScope {
      override def suppliedUrl = None
      override def ormDocumentId = 4L
      urlWithSimplePattern must beEqualTo("https://localhost/4")
    }

    "give no suppliedUrl for a CsvImportDocument that has none" in new CsvImportDocumentScope {
      override def suppliedUrl = None
      csvImportDocument.suppliedUrl must beNone
    }

    "give a suppliedUrl for a CsvImportDocument if there is one" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("http://example.org")
      csvImportDocument.suppliedUrl must beSome("http://example.org")
    }

    "give no secureSuppliedUrl for a CsvImportDocument if there is no suppliedUrl" in new CsvImportDocumentScope {
      override def suppliedUrl = None
      csvImportDocument.secureSuppliedUrl must beNone
    }

    "give no secureSuppliedUrl for a CsvImportDocument if the suppleidUrl is not https" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("http://example.org")
      csvImportDocument.secureSuppliedUrl must beNone
    }

    "give a secureSuppliedUrl for a CsvImportDocument" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("https://example.org")
      csvImportDocument.secureSuppliedUrl must beSome("https://example.org")
    }

    "give the proper url for a DocumentCloudDocument" in new DocumentCloudDocumentScope {
      override def ormDocument = super.ormDocument.copy(documentcloudId=Some("foobar"))
      urlWithSimplePattern must beEqualTo("https://www.documentcloud.org/documents/foobar")
    }
  }

  step(stop)
}
