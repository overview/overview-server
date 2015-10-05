package controllers

import java.io.FileInputStream
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.{DocumentBackend,DocumentSetBackend,DocumentTagBackend,TagBackend}
import models.export.rows.Rows
import models.export.format.Format
import com.overviewdocs.util.TempFile

class DocumentSetExportControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockDocumentBackend = smartMock[DocumentBackend]
    val mockDocumentSetBackend = smartMock[DocumentSetBackend]
    val mockDocumentTagBackend = smartMock[DocumentTagBackend]
    val mockTagBackend = smartMock[TagBackend]

    val controller = new DocumentSetExportController with TestController {
      override val documentBackend = mockDocumentBackend
      override val documentSetBackend = mockDocumentSetBackend
      override val documentTagBackend = mockDocumentTagBackend
      override val tagBackend = mockTagBackend
    }

    def request = fakeAuthorizedRequest

    val factory = com.overviewdocs.test.factories.PodoFactory
  }

  "DocumentSetExportController" should {
    trait ExportScope extends BaseScope {
      val documentSet = factory.documentSet()

      val doc1 = (factory.document(), Seq(5L, 6L))
      val doc2 = (factory.document(), Seq(5L))

      val tags = Seq(
        factory.tag(id=5L, name="tag five"),
        factory.tag(id=6L, name="tag six"),
        factory.tag(id=7L, name="tag seven")
      )

      val contents = "id,name\n1,foo".getBytes
      val filename = "foobar.csv"

      mockDocumentSetBackend.show(45L) returns Future.successful(Some(documentSet))
      mockTagBackend.index(45L) returns Future.successful(tags)

      val format = smartMock[Format]
      format.contentType returns "text/csv; charset=\"utf-8\""
      format.bytes(any) returns Enumerator(contents)
    }

    "#documentsWithStringTags" should {
      trait DocumentsWithStringTagsScope extends ExportScope {
        lazy val result = controller.documentsWithStringTags(format, filename, 45L)(request)
      }

      "set Content-Type header" in new DocumentsWithStringTagsScope {
        h.header(h.CONTENT_TYPE, result) must beSome("text/csv; charset=\"utf-8\"")
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
