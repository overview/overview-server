package models

import org.overviewproject.test.DbSpecification
import org.specs2.specification.Scope
import play.api.Play.{start,stop}
import play.api.test.FakeApplication

// OverviewDocument wraps models.orm.Document. Let's be transparent about that
// in this test.
import org.overviewproject.tree.orm.{ Document, DocumentType }

class OverviewDocumentSpec extends DbSpecification {
  step(start(FakeApplication()))

  "OverviewDocument" should {
    trait OneDocument {
      def ormDocument: Document
      lazy val document = OverviewDocument(ormDocument)

      lazy val urlWithSimplePattern = document.urlWithFallbackPattern("https://localhost/{0}")
    }

    trait CsvImportDocumentScope extends Scope with OneDocument {
      def ormDocumentId: Long = 1L
      def suppliedId: Option[String] = None
      def suppliedUrl: Option[String] = Some("http://example.org")
      def description: String = "description"
      def title: Option[String] = Some("title")
      def text: String = "Text"
      
      override def ormDocument = Document(
        DocumentType.CsvImportDocument,
        id=ormDocumentId,
        url=suppliedUrl,
        suppliedId=suppliedId,
        description=description,
        title=title,
        text=Some(text)
      )
      override lazy val document = OverviewDocument(ormDocument)
    }

    trait DocumentCloudDocumentScope extends Scope with OneDocument {
      def ormDocumentId: Long = 1L
      def documentcloudId: String = "123-documentcloud-id"

      override def ormDocument = Document(
        DocumentType.DocumentCloudDocument,
        id=ormDocumentId,
        documentcloudId=Some(documentcloudId)
      )
      override lazy val document = OverviewDocument(ormDocument)
    }

    "give the proper url for a Document with a url" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("https://example.org/foo")
      urlWithSimplePattern must beEqualTo("https://example.org/foo")
    }

    "give the proper url for a Document with no url" in new CsvImportDocumentScope {
      override def suppliedUrl = None
      override def ormDocumentId = 4L
      urlWithSimplePattern must beEqualTo("https://localhost/4")
    }

    "give no url for a Document that has none" in new CsvImportDocumentScope {
      override def suppliedUrl = None
      document.url must beNone
    }

    "give no suppliedId for a CsvImport document that has none" in new CsvImportDocumentScope {
      document.suppliedId must beNone
    }

    "give a suppliedId for a Document that has one" in new CsvImportDocumentScope {
      override def suppliedId = Some("1")
      document.suppliedId must beSome("1")
    }

    "give a suppliedId with the documentcloudId" in new DocumentCloudDocumentScope {
      document.suppliedId must beSome(documentcloudId)
    }

    "give the proper url for a DocumentCloudDocument" in new DocumentCloudDocumentScope {
      override def ormDocument = super.ormDocument.copy(documentcloudId=Some("foobar"))
      document.url must beSome("https://www.documentcloud.org/documents/foobar")
    }
    
    "give a title for a Document if there is one" in new CsvImportDocumentScope {
      document.title must be equalTo title
    }
  }

  step(stop)
}
