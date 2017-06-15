package controllers

import java.io.FileInputStream
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.{DocumentBackend,DocumentSetBackend,DocumentTagBackend,TagBackend,SelectionBackend}
import models.InMemorySelection
import models.export.rows.Rows
import models.export.format.CsvFormat

class DocumentSetExportControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockDocumentBackend = smartMock[DocumentBackend]
    val mockDocumentSetBackend = smartMock[DocumentSetBackend]
    val mockDocumentTagBackend = smartMock[DocumentTagBackend]
    val mockTagBackend = smartMock[TagBackend]
    val mockSelectionBackend = smartMock[SelectionBackend]
    def selection: InMemorySelection = ???
    mockSelectionBackend.findOrCreate(any, any, any) returns Future { selection }

    val controller = new DocumentSetExportController(
      mockDocumentBackend,
      mockDocumentSetBackend,
      mockDocumentTagBackend,
      mockTagBackend,
      mockSelectionBackend,
      testMessagesApi
    )

    def request = fakeAuthorizedRequest

    val factory = com.overviewdocs.test.factories.PodoFactory
  }

  "DocumentSetExportController" should {
    trait ExportScope extends BaseScope {
      val documentSet = factory.documentSet()

      val doc1 = factory.document(id=1L, suppliedId="11", title="doc1", text="text1", url=None)
      val doc2 = factory.document(id=2L, suppliedId="22", title="doc2", text="text2", url=None)

      override def selection = InMemorySelection(Array(1L, 2L))
      mockDocumentBackend.index(any, any) returns Future.successful(Seq(doc1, doc2))
      mockDocumentTagBackend.indexMany(any) returns Future.successful(Map(
        1L -> Seq(5L, 6L),
        2L -> Seq(5L)
      ))

      val tags = Seq(
        factory.tag(id=5L, name="tag five"),
        factory.tag(id=6L, name="tag six"),
        factory.tag(id=7L, name="tag seven")
      )

      val filename = "foobar.csv"

      mockDocumentSetBackend.show(45L) returns Future.successful(Some(documentSet))
      mockTagBackend.index(45L) returns Future.successful(tags)

      // Integration-test-ish: we'll actually output a CSV to test all our
      // queries work
      val format = CsvFormat
    }

    "#documentsWithStringTags" should {
      trait DocumentsWithStringTagsScope extends ExportScope {
        lazy val result = controller.documentsWithStringTags(format, filename, 45L)(request)
      }

      "set Content-Type header" in new DocumentsWithStringTagsScope {
        h.header(h.CONTENT_TYPE, result) must beSome("text/csv; charset=\"utf-8\"")
      }

      "set contents" in new DocumentsWithStringTagsScope {
        val contents = "\ufeffid,title,text,url,tags\r\n11,doc1,text1,,\"tag five,tag six\"\r\n22,doc2,text2,,tag five\r\n"
        h.contentAsString(result) must beEqualTo(contents)
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
        val contents = "\ufeffid,title,text,url,tag five,tag six,tag seven\r\n11,doc1,text1,,1,1,\r\n22,doc2,text2,,1,,\r\n"
        h.contentAsString(result) must beEqualTo(contents)
      }

      "not send as chunked" in new DocumentsWithColumnTagsScope {
        h.header(h.TRANSFER_ENCODING, result) must beNone
      }
    }
  }
}
