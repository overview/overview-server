package models

import org.overviewproject.test.DbSpecification
import org.specs2.specification.Scope
import play.api.Play.{start,stop}
import play.api.test.{FakeApplication,WithApplication}

// OverviewDocument wraps models.orm.Document. Let's be transparent about that
// in this test.
import org.overviewproject.tree.orm.Document

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
        id=ormDocumentId,
        documentcloudId=Some(documentcloudId)
      )
      override lazy val document = OverviewDocument(ormDocument)
    }
    
    trait UploadedDocumentScope extends Scope with OneDocument {
      val ormDocumentId: Long = 1L
      val contentsOid = 12345L
      val contentLength = 22000L
      
      override def ormDocument = Document(
        id = ormDocumentId,
        contentsOid = Some(contentsOid),
        contentLength = Some(contentLength)
      )
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
    
    "give the proper url for an uploaded document" in new UploadedDocumentScope {
      document.url must beSome(s"/documents/$ormDocumentId/contents/$contentsOid")
    }
    "give a title for a Document if there is one" in new CsvImportDocumentScope {
      document.title must be equalTo title
    }
  }

  step(stop)

  "OverviewDocument with nonstandard DocumentCloud URL" should {
    "give the proper URL when a custom DocumentCloud URL is configured" in new WithApplication(FakeApplication(additionalConfiguration = Map("overview.documentcloud_url" -> "https://foo.bar"))) {
      val ormDocument = Document(
        id=1L,
        documentcloudId=Some("123-foobar")
      )
      val document = OverviewDocument(ormDocument)
      document.url must beSome("https://foo.bar/documents/123-foobar")
    }
  }
}
