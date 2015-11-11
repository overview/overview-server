package controllers

import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsValue,Json}
import play.api.mvc.AnyContent
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.{DocumentBackend,FileBackend,PageBackend}
import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.{Document,File,Page}

class DocumentControllerSpec extends ControllerSpecification with JsonMatchers {
  trait DocumentScope extends Scope {
    val mockDocumentBackend = mock[DocumentBackend]
    val mockFileBackend = mock[FileBackend]
    val mockPageBackend = mock[PageBackend]
    val mockBlobStorage = mock[BlobStorage]

    val controller = new DocumentController with TestController {
      override val blobStorage = mockBlobStorage
      override val documentBackend = mockDocumentBackend
      override val fileBackend = mockFileBackend
      override val pageBackend = mockPageBackend
    }

    val factory = com.overviewdocs.test.factories.PodoFactory

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

      "set Generated-By tesseract if isFromOcr=true" in new ShowTextScope {
        override def foundDocument = Some(factory.document(text="foo", isFromOcr=true))
        h.header("Generated-By", result) must beSome("tesseract")
      }

      "not set Generated-By tesseract if isFromOcr=false" in new ShowTextScope {
        override def foundDocument = Some(factory.document(text="foo", isFromOcr=false))
        h.header("Generated-By", result) must beNone
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

    "update()" should {
      trait UpdateScope extends DocumentScope {
        val documentSetId = 123L
        val documentId = 124L

        mockDocumentBackend.updateTitle(any, any, any) returns Future.successful(())
        mockDocumentBackend.updateMetadataJson(any, any, any) returns Future.successful(())

        def input: JsValue = Json.obj()
        lazy val result = controller.update(documentSetId, documentId)(fakeAuthorizedRequest.withJsonBody(input))
      }

      "set new metadataJson in the backend" in new UpdateScope {
        override def input = Json.obj("metadata" -> Json.obj("foo" -> "bar"))
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockDocumentBackend).updateMetadataJson(documentSetId, documentId, Json.obj("foo" -> "bar"))
        there was no(mockDocumentBackend).updateTitle(any, any, any)
      }

      "throw a 400 when the input is not JSON" in new UpdateScope {
        override lazy val result = controller.update(documentSetId, documentId)(fakeAuthorizedRequest)
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        there was no(mockDocumentBackend).updateMetadataJson(any, any, any)
      }

      "throw a 400 when the JSON is not an Object" in new UpdateScope {
        override def input = Json.arr("foo", "bar")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must /("code" -> "illegal-arguments")
        h.contentAsString(result) must /("message" -> "You must pass a JSON Object with a \"metadata\" property or a \"title\" property")
        there was no(mockDocumentBackend).updateMetadataJson(any, any, any)
      }

      "throw a 400 when the input does not contain metadata" in new UpdateScope {
        // when update() allows both "title" and "metadata", this will test what
        // happens when neither is specified
        override def input = Json.obj("metadatblah" -> Json.obj("foo" -> "bar"))
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must /("code" -> "illegal-arguments")
        h.contentAsString(result) must /("message" -> "You must pass a JSON Object with a \"metadata\" property or a \"title\" property")
        there was no(mockDocumentBackend).updateMetadataJson(any, any, any)
      }

      "set new title in the backend" in new UpdateScope {
        override def input = Json.obj("title" -> "foo")
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockDocumentBackend).updateTitle(documentSetId, documentId, "foo")
        there was no(mockDocumentBackend).updateMetadataJson(any, any, any)
      }

      "throw a 400 when the title is not a String" in new UpdateScope {
        override def input = Json.obj("title" -> Json.arr("foo", "bar"))
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must /("code" -> "illegal-arguments")
        h.contentAsString(result) must /("message" -> "You must pass a JSON Object with a \"metadata\" property or a \"title\" property")
        there was no(mockDocumentBackend).updateTitle(any, any, any)
      }
    }
  }
}
