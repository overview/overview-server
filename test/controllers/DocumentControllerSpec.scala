package controllers

import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.mvc.AnyContent
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.{DocumentBackend,FileBackend,PageBackend}
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.{Document,File,Page}

class DocumentControllerSpec extends ControllerSpecification {
  trait DocumentScope extends Scope {
    val mockDocumentBackend = mock[DocumentBackend]
    val mockFileBackend = mock[FileBackend]
    val mockPageBackend = mock[PageBackend]
    val mockBlobStorage = mock[BlobStorage]

    val controller = new DocumentController {
      override val blobStorage = mockBlobStorage
      override val documentBackend = mockDocumentBackend
      override val fileBackend = mockFileBackend
      override val pageBackend = mockPageBackend
    }

    val factory = org.overviewproject.test.factories.PodoFactory

    val request : AuthorizedRequest[AnyContent] = fakeAuthorizedRequest

    val requestedDocumentId = 1L
    val fileId = 2L
    val pageId = 3L
    val validLocation = "foo:bar"

    def foundDocument: Option[Document] = None
    def foundFile: Option[File] = None
    def foundPage: Option[Page] = None
    def foundBlob: Enumerator[Array[Byte]] = Enumerator.empty

    mockDocumentBackend.show(requestedDocumentId) returns Future { foundDocument }
    mockFileBackend.show(fileId) returns Future { foundFile }
    mockPageBackend.show(pageId) returns Future { foundPage }
    mockBlobStorage.get(validLocation) returns Future { foundBlob }
  }

  "DocumentController" should {
    "showText()" should {
      trait ShowTextScope extends DocumentScope {
        lazy val result = controller.showText(requestedDocumentId)(request)
      }

      "return NotFound when ID is invalid" in new ShowTextScope {
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return the text when the ID is valid" in new ShowTextScope {
        override def foundDocument = Some(factory.document(text="foo bar"))
        h.status(result) must beEqualTo(h.OK)
        h.contentType(result) must beSome("text/plain")
        h.charset(result) must beSome("utf-8")
        h.contentAsString(result) must beEqualTo("foo bar")
      }
    }

    "showPdf()" should {
      trait ShowPdfScope extends DocumentScope {
        lazy val result = controller.showPdf(requestedDocumentId)(request)
      }

      "return NotFound when ID is invalid" in new ShowPdfScope {
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return no content when document has no file or page" in new ShowPdfScope {
        override def foundDocument = Some(factory.document(fileId=None, pageId=None))
        h.status(result) must beEqualTo(h.OK)
        h.header("Content-Length", result) must beSome("0")
        h.contentAsString(result) must beEqualTo("")
      }

      "return empty content when the page does not exist" in new ShowPdfScope {
        override def foundDocument = Some(factory.document(fileId=Some(fileId), pageId=Some(pageId)))
        override def foundPage = None
        h.status(result) must beEqualTo(h.OK)
        h.header("Content-Length", result) must beSome("0")
        h.contentAsString(result) must beEqualTo("")
      }

      "return page content from blob storage" in new ShowPdfScope {
        override def foundDocument = Some(factory.document(fileId=Some(fileId), pageId=Some(pageId)))
        override def foundPage = Some(factory.page(dataLocation=validLocation, dataSize=9))
        override def foundBlob = Enumerator("page data".getBytes("utf-8"))

        h.status(result) must beEqualTo(h.OK)
        h.header("Content-Length", result) must beSome("9")
        h.contentAsString(result) must beEqualTo("page data")
      }
    }
  }
}
