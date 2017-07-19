package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import play.api.libs.json.{JsValue,Json}
import play.api.mvc.AnyContent
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.{DocumentBackend,FileBackend,PageBackend}
import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.{Document,File,Page,PdfNote,PdfNoteCollection}

class DocumentControllerSpec extends ControllerSpecification with JsonMatchers {
  trait DocumentScope extends Scope {
    val mockDocumentBackend = smartMock[DocumentBackend]
    val mockFileBackend = smartMock[FileBackend]
    val mockPageBackend = smartMock[PageBackend]
    val mockBlobStorage = smartMock[BlobStorage]

    val controller = new DocumentController(
      mockDocumentBackend,
      mockBlobStorage,
      mockFileBackend,
      mockPageBackend,
      fakeControllerComponents,
      app.configuration,
      mockView[views.html.Document.show]
    )

    val factory = com.overviewdocs.test.factories.PodoFactory

    val request : AuthorizedRequest[AnyContent] = fakeAuthorizedRequest

    val requestedDocumentId = 1L
    val fileId = 2L
    val pageId = 3L
    val validLocation = "foo:bar"

    def foundDocument: Option[Document] = None
    def foundFile: Option[File] = None
    def foundPage: Option[Page] = None
    def foundBlob: Source[ByteString, akka.NotUsed] = Source.empty

    mockDocumentBackend.show(requestedDocumentId) returns Future { foundDocument }
    mockFileBackend.show(fileId) returns Future { foundFile }
    mockPageBackend.show(pageId) returns Future { foundPage }
    mockBlobStorage.get(validLocation) returns foundBlob
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
        result.value.get.get.body.contentLength must beSome(0)
        h.contentAsString(result) must beEqualTo("")
      }

      "return empty content when the page does not exist" in new ShowPdfScope {
        override def foundDocument = Some(factory.document(fileId=Some(fileId), pageId=Some(pageId)))
        override def foundPage = None
        h.status(result) must beEqualTo(h.OK)
        result.value.get.get.body.contentLength must beSome(0)
        h.contentAsString(result) must beEqualTo("")
      }

      "return page content from blob storage" in new ShowPdfScope {
        override def foundDocument = Some(factory.document(fileId=Some(fileId), pageId=Some(pageId)))
        override def foundPage = Some(factory.page(dataLocation=validLocation, dataSize=9))
        override def foundBlob = Source.single(ByteString("page data".getBytes("utf-8")))

        h.status(result) must beEqualTo(h.OK)
        result.value.get.get.body.contentLength must beSome(9)
        h.contentAsString(result) must beEqualTo("page data")
      }
    }

    "showPdfNotes()" should {
      trait ShowPdfNotesScope extends DocumentScope {
        lazy val result = controller.showPdfNotes(requestedDocumentId)(request)
      }

      "return NotFound when document is not found" in new ShowPdfNotesScope {
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return notes" in new ShowPdfNotesScope {
        override def foundDocument = Some(factory.document(pdfNotes=PdfNoteCollection(Array(
          PdfNote(1, 2.0, 3.0, 4.0, 5.0, "foo")
        ))))

        h.status(result) must beEqualTo(h.OK)
        h.contentAsString(result) must beEqualTo("""[{"pageIndex":1,"x":2,"y":3,"width":4,"height":5,"text":"foo"}]""")
      }
    }

    "update()" should {
      trait UpdateScope extends DocumentScope {
        val documentSetId = 123L
        val documentId = 124L

        mockDocumentBackend.updateTitle(any, any, any) returns Future.unit
        mockDocumentBackend.updateMetadataJson(any, any, any) returns Future.unit

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

    "updatePdfNotes" should {
      trait UpdatePdfNotesScope extends DocumentScope {
        val documentId = 124L

        mockDocumentBackend.updatePdfNotes(any, any) returns Future.unit

        def input: JsValue = Json.arr()
        lazy val result = controller.updatePdfNotes(documentId)(fakeAuthorizedRequest.withJsonBody(input))
      }

      "return 400 on non-JSON input" in new UpdatePdfNotesScope {
        // no data
        override lazy val result = controller.updatePdfNotes(documentId)(fakeAuthorizedRequest)
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must /("code" -> "illegal-arguments")
        h.contentAsString(result) must /("message" -> "You must pass a JSON Array of {pageIndex,x,y,width,height,text} Objects")
        there was no(mockDocumentBackend).updateMetadataJson(any, any, any)
      }

      "return 400 when input is invalid" in new UpdatePdfNotesScope {
        override val input = Json.obj()
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "set new pdfNotes in the backend" in new UpdatePdfNotesScope {
        val noteJson = Json.obj(
          "pageIndex" -> 1,
          "x" -> 2.0,
          "y" -> 3.0,
          "width" -> 4.0,
          "height" -> 5.0,
          "text" -> "Foo"
        )

        override def input = Json.arr(noteJson)
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockDocumentBackend).updatePdfNotes(124L, PdfNoteCollection(Array(
          PdfNote(1, 2.0, 3.0, 4.0, 5.0, "Foo")
        )))
      }
    }
  }
}
