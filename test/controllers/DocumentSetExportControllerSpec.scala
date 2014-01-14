package controllers

import java.io.FileInputStream

import play.api.Play.{ start, stop }
import play.api.mvc.{ AnyContent, Request }
import play.api.test.{ FakeApplication, FakeRequest }
import play.api.test.Helpers._

import org.overviewproject.tree.orm.{Document,DocumentSet,Tag}
import org.overviewproject.tree.orm.finders.FinderResult
import org.overviewproject.util.TempFile
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import controllers.auth.AuthorizedRequest
import models.OverviewUser
import models.export.Export
import models.export.rows.Rows
import models.export.format.{CsvFormat,Format}

class DocumentSetExportControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  def makeFileInputStream(contents: Array[Byte]) : FileInputStream = {
    val tempFile = new TempFile
    tempFile.outputStream.write(contents)
    tempFile.outputStream.close
    tempFile.inputStream
  }

  trait BaseScope extends Scope {
    val mockStorage = mock[DocumentSetExportController.Storage]
    val mockRowsCreator = mock[DocumentSetExportController.RowsCreator]
    val mockExport = mock[Export]

    val controller = new DocumentSetExportController {
      override val storage = mockStorage
      override val rowsCreator = mockRowsCreator
      override def createExport(rows: Rows, format: Format) = mockExport
    }

    val user = mock[OverviewUser]
    def fakeRequest : Request[AnyContent] = FakeRequest()
    def request = new AuthorizedRequest(fakeRequest, user)
  }

  trait IndexScope extends BaseScope {
    mockStorage.findDocumentSet(45L) returns Some(DocumentSet(title="foobar"))
    lazy val result = controller.index(45)(request)
  }

  trait DocumentsWithStringTagsScope extends BaseScope {
    val documentSetId = 1L
    val finderResult = mock[FinderResult[(Document,Option[String])]]
    mockStorage.loadDocumentsWithStringTags(any[Long]) returns finderResult

    val contents = "id,name\n1,foo".getBytes
    mockExport.contentType returns "text/csv; charset=\"utf-8\""
    mockExport.asFileInputStream returns makeFileInputStream(contents)
    mockRowsCreator.documentsWithStringTags(any[FinderResult[(Document,Option[String])]]) returns mock[Rows]

    val filename = "foobar.csv"

    lazy val result = controller.documentsWithStringTags(CsvFormat, filename, documentSetId)(request)
  }

  trait DocumentsWithColumnTagsScope extends BaseScope {
    val documentSetId = 1L
    val finderResult = mock[FinderResult[(Document,Option[String])]]
    val tagFinderResult = mock[FinderResult[Tag]]
    mockStorage.loadDocumentsWithTagIds(any[Long]) returns finderResult
    mockStorage.loadTags(any[Long]) returns tagFinderResult

    val contents = "id,name\n1,foo".getBytes
    mockExport.contentType returns "text/csv; charset=\"utf-8\""
    mockExport.asFileInputStream returns makeFileInputStream(contents)
    mockRowsCreator.documentsWithColumnTags(any[FinderResult[(Document,Option[String])]], any[FinderResult[Tag]]) returns mock[Rows]

    val filename = "foobar.csv"

    lazy val result = controller.documentsWithColumnTags(CsvFormat, filename, documentSetId)(request)
  }

  "DocumentSetExportController" should {
    "use the title in output filenames" in new IndexScope {
      // Icky: tests the view, really
      contentAsString(result) must contain("foobar.csv")
    }

    "HTTP-path-encode question marks in output filenames" in new IndexScope {
      // Icky: tests the view, really
      // The problem: somebody requests "what?.csv", then HTTP servers like
      // Play will treat everything after the ? as a path. 
      mockStorage.findDocumentSet(45L) returns Some(DocumentSet(title="foo?bar"))
      contentAsString(result) must contain("foo_bar.csv")
    }

    "replace icky filename characters with underscores" in new IndexScope {
      // Icky: tests the view, really
      // The problem: the server serves up a file with Content-Disposition
      // "foo/bar.csv", and the client is not allowed to save it because "/"
      // is a reserved character.

      // List from http://en.wikipedia.org/wiki/Filename#Reserved_characters_and_words
      // When in doubt, escape it!
      mockStorage.findDocumentSet(45L) returns Some(DocumentSet(title="foo/\n\\?%*:|\"<>bar"))
      contentAsString(result) must contain("foo___________bar.csv")
    }

    "replace dots with underscores" in new IndexScope {
      // Icky: tests the view, really
      // The problem: if the server serves up foobar.csv.xlsx, virus scanners
      // will complain that the file is masquerading as something else.
      mockStorage.findDocumentSet(45L) returns Some(DocumentSet(title="foo.bar"))
      contentAsString(result) must contain("foo_bar.csv")
    }

    "set Content-Type header in documentsWithStringTags" in new DocumentsWithStringTagsScope {
      header(CONTENT_TYPE, result) must beSome(mockExport.contentType)
    }

    "set Content-Length header in documentsWithStringTags" in new DocumentsWithStringTagsScope {
      header(CONTENT_LENGTH, result) must beSome(contents.size.toString)
    }

    "set contents of documentsWithStringTags" in new DocumentsWithStringTagsScope {
      contentAsBytes(result) must beEqualTo(contents)
    }

    "not send as chunked in documentsWithStringTags" in new DocumentsWithStringTagsScope {
      header(TRANSFER_ENCODING, result) must beNone
    }

    "set Content-Type header in documentsWithColumnTags" in new DocumentsWithColumnTagsScope {
      header(CONTENT_TYPE, result) must beSome(mockExport.contentType)
    }

    "set Content-Length header in documentsWithColumnTags" in new DocumentsWithColumnTagsScope {
      header(CONTENT_LENGTH, result) must beSome(contents.size.toString)
    }

    "set the proper Content-Disposition" in new DocumentsWithColumnTagsScope {
      header(CONTENT_DISPOSITION, result) must beSome("""attachment; filename="foobar.csv"""")
    }

    "set a Content-Disposition with unencoded HTTP paths" in new DocumentsWithColumnTagsScope {
      // Play will not decode path parameters. See https://github.com/playframework/playframework/issues/1228
      override val filename = "foo%20bar.csv"
      header(CONTENT_DISPOSITION, result) must beSome("attachment; filename*=UTF-8''foo%20bar.csv")
    }

    "set contents of documentsWithColumnTags" in new DocumentsWithColumnTagsScope {
      contentAsBytes(result) must beEqualTo(contents)
    }

    "not send as chunked in documentsWithColumnTags" in new DocumentsWithColumnTagsScope {
      header(TRANSFER_ENCODING, result) must beNone
    }
  }

  step(stop)
}
