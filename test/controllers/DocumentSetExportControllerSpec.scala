package controllers

import java.io.FileInputStream
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.{DocumentSetBackend,TagBackend}
import models.export.rows.{DocumentForCsvExport,Rows}
import models.export.format.Format
import com.overviewdocs.util.TempFile

class DocumentSetExportControllerSpec extends ControllerSpecification {
  def makeFileInputStream(contents: Array[Byte]): FileInputStream = {
    val tempFile = new TempFile
    tempFile.outputStream.write(contents)
    tempFile.outputStream.close
    tempFile.inputStream
  }

  trait BaseScope extends Scope {
    val mockStorage = smartMock[DocumentSetExportController.Storage]
    val mockDocumentSetBackend = smartMock[DocumentSetBackend]
    val mockTagBackend = smartMock[TagBackend]

    val controller = new DocumentSetExportController with TestController {
      override val documentSetBackend = mockDocumentSetBackend
      override val tagBackend = mockTagBackend
      override val storage = mockStorage
    }

    def request = fakeAuthorizedRequest

    val factory = com.overviewdocs.test.factories.PodoFactory
  }

  "DocumentSetExportController" should {
    trait ExportScope extends BaseScope {
      val documentSet = factory.documentSet()

      val doc1 = DocumentForCsvExport("1", "2", "3", "4", Json.obj(), Seq(5L, 6L))
      val doc2 = DocumentForCsvExport("7", "8", "9", "0", Json.obj(), Seq())
      val documents = Enumerator(doc1, doc2)

      val tags = Seq(
        factory.tag(id=5L, name="tag five"),
        factory.tag(id=6L, name="tag six"),
        factory.tag(id=7L, name="tag seven")
      )

      val contents = "id,name\n1,foo".getBytes
      val filename = "foobar.csv"
      val fileInputStream = makeFileInputStream(contents)

      mockDocumentSetBackend.show(45L) returns Future.successful(Some(documentSet))
      mockTagBackend.index(45L) returns Future.successful(tags)
      mockStorage.streamDocumentsWithTagIds(any) returns Future.successful(documents)

      val format = smartMock[Format]
      format.contentType returns "text/csv; charset=\"utf-8\""
      format.getContentsAsInputStream(any) returns Future.successful(fileInputStream)
    }

    "#documentsWithStringTags" should {
      trait DocumentsWithStringTagsScope extends ExportScope {
        lazy val result = controller.documentsWithStringTags(format, filename, 45L)(request)
      }

      "set Content-Type header" in new DocumentsWithStringTagsScope {
        h.header(h.CONTENT_TYPE, result) must beSome("text/csv; charset=\"utf-8\"")
      }

      "set Content-Length header" in new DocumentsWithStringTagsScope {
        h.header(h.CONTENT_LENGTH, result) must beSome(contents.size.toString)
      }

      "set contents" in new DocumentsWithStringTagsScope {
        h.contentAsBytes(result) must beEqualTo(contents)
      }

      "not send as chunked" in new DocumentsWithStringTagsScope {
        h.header(h.TRANSFER_ENCODING, result) must beNone
      }
    }

    "#documentsWithColumnTags" should {
      trait DocumentsWithColumnTagsScope extends ExportScope {
        lazy val result = controller.documentsWithColumnTags(format, filename, 45L)(request)
      }

      "set Content-Type header" in new DocumentsWithColumnTagsScope {
        h.header(h.CONTENT_TYPE, result) must beSome("text/csv; charset=\"utf-8\"")
      }

      "set Content-Length header" in new DocumentsWithColumnTagsScope {
        h.header(h.CONTENT_LENGTH, result) must beSome(contents.size.toString)
      }

      "set the proper Content-Disposition" in new DocumentsWithColumnTagsScope {
        h.header(h.CONTENT_DISPOSITION, result) must beSome("""attachment; filename="foobar.csv"""")
      }

      "set a Content-Disposition with special characters escaped" in new DocumentsWithColumnTagsScope {
        override val filename = "foo [bar.csv"
        h.header(h.CONTENT_DISPOSITION, result) must beSome("attachment; filename*=UTF-8''foo%20%5Bbar.csv")
      }

      "set contents" in new DocumentsWithColumnTagsScope {
        h.contentAsBytes(result) must beEqualTo(contents)
      }

      "not send as chunked" in new DocumentsWithColumnTagsScope {
        h.header(h.TRANSFER_ENCODING, result) must beNone
      }
    }
  }
}
