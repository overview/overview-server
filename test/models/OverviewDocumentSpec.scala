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

      lazy val urlWithSimplePattern = document.url("https://localhost/{0}")
    }

    trait CsvImportDocumentScope extends Scope with OneDocument {
      def ormDocumentId: Long = 1L
      def suppliedUrl: Option[String] = Some("http://example.org")
      def description: String = "description"
      def title: Option[String] = Some("title")
      def text: String = "Text"
      
      override def ormDocument = Document(
        new DocumentType("CsvImportDocument"),
        id=ormDocumentId,
        url=suppliedUrl,
        description=description,
        title=title,
        text=Some(text)
      )
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

    "give a description when there is no title" in new CsvImportDocumentScope {
      override def description = "description"
      override def title = None
      csvImportDocument.titleOrDescription must beEqualTo("description")
    }

    "give a title when there is one" in new CsvImportDocumentScope {
      override def title = Some("title")
      csvImportDocument.titleOrDescription must beEqualTo("title")
    }

    "give no secureSuppliedUrl for a CsvImportDocument if the suppliedUrl is not https" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("http://example.org")
      csvImportDocument.secureSuppliedUrl must beNone
    }

    "give a secureSuppliedUrl for a CsvImportDocument" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("https://example.org")
      csvImportDocument.secureSuppliedUrl must beSome("https://example.org")
    }

    "give no twitterTweet for a CsvImportDocument if there is no suppliedUrl" in new CsvImportDocumentScope {
      override def suppliedUrl = None
      csvImportDocument.twitterTweet must beNone
    }

    "give no twitterTweet for a CsvImportDocument if the suppliedUrl is not a Twitter one" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("https://example.org")
      csvImportDocument.twitterTweet must beNone
    }

    "give a twitterTweet for an http://twitter.com URL" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("http://twitter.com/adamhooper/status/1234")
      csvImportDocument.twitterTweet must beSome[TwitterTweet]
    }

    "give a twitterTweet for an https://twitter.com URL" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("https://twitter.com/adamhooper/status/1234")
      csvImportDocument.twitterTweet must beSome[TwitterTweet]
    }

    "give a twitterTweet for an http://www.twitter.com URL" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("http://www.twitter.com/adamhooper/status/1234")
      csvImportDocument.twitterTweet must beSome[TwitterTweet]
    }

    "initialize a twitterTweet with the correct url and text" in new CsvImportDocumentScope {
      override def suppliedUrl = Some("https://twitter.com/adamhooper/status/1234")
      val twitterTweet = csvImportDocument.twitterTweet.getOrElse(throw new Exception(".twitterTweet() is broken..."))
      twitterTweet.url must beEqualTo(suppliedUrl.getOrElse(throw new Exception("Some() is broken...")))
      twitterTweet.text must beEqualTo(csvImportDocument.text)
    }

    "give the proper url for a DocumentCloudDocument" in new DocumentCloudDocumentScope {
      override def ormDocument = super.ormDocument.copy(documentcloudId=Some("foobar"))
      urlWithSimplePattern must beEqualTo("https://www.documentcloud.org/documents/foobar")
    }
    
    "give a title for a Document if there is one" in new CsvImportDocumentScope {
      document.title must be equalTo title
    }
  }

  step(stop)
}
