package views.json.DocumentList

import java.util.UUID

import org.specs2.matcher.{JsonMatchers,Matcher}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.{Json,Writes}

import com.overviewdocs.models.{File2,FullDocumentInfo,PdfNote,PdfNoteCollection}
import com.overviewdocs.query.Field
import com.overviewdocs.searchindex.{SearchWarning,Utf16Highlight,Utf16Snippet}
import com.overviewdocs.test.factories.{PodoFactory => factory}
import models.{InMemorySelection,SelectionWarning}
import models.pagination.Page

class showSpec extends Specification with JsonMatchers {
  trait BaseScope extends Scope {
    val selectionId = UUID.fromString("933c0b0b-fd89-4ed3-ad4a-731bbb04da43")
    def doc1 = factory.document()
    def doc2 = factory.document()
    def warnings: List[SelectionWarning] = List()
    def selection = InMemorySelection(selectionId, Vector(doc1.id, doc2.id), warnings)

    def doc1AndIds = (doc1, None.asInstanceOf[Option[String]], Vector[Long](), Vector[Long](), Vector[Utf16Snippet](), None.asInstanceOf[Option[File2]], None.asInstanceOf[Option[FullDocumentInfo]])
    def doc2AndIds = (doc2, None.asInstanceOf[Option[String]], Vector[Long](), Vector[Long](), Vector[Utf16Snippet](), None.asInstanceOf[Option[File2]], None.asInstanceOf[Option[FullDocumentInfo]])
    def docsAndIds = Vector(doc1AndIds, doc2AndIds)

    def resultPage = Page(docsAndIds)

    def result = show(selection, resultPage).toString
  }

  "DocumentList view generated Json" should {
    "contain total_items" in new BaseScope {
      // This gave a NullPointerException on 2014-05-20
      //result must /("total_items" -> 2)
      // So we do this instead:
      result must contain(""""total_items":2""")
    }

    "contain selection_id" in new BaseScope {
      result must contain(""""selection_id":"933c0b0b-fd89-4ed3-ad4a-731bbb04da43"""")
    }

    "contain warnings" in new BaseScope {
      override def warnings = List(SelectionWarning.SearchIndexWarning(SearchWarning.TooManyExpansions(Field.Text, "bar*", 10)))
      result must /("warnings") /#(0) /("type" -> "TooManyExpansions")
      result must /("warnings") /#(0) /("field" -> "text")
      result must /("warnings") /#(0) /("term" -> "bar*")
      result must /("warnings") /#(0) /("nExpansions" -> 10)
    }

    "contain documents" in new BaseScope {
      override def doc2 = factory.document(id=2L)
      result must /("documents") /#(1) /("id" -> 2)
    }

    "set a title" in new BaseScope {
      override def doc1 = factory.document(title="aTitle")
      result must /("documents") /#(0) /("title" -> "aTitle")
    }

    "set a documentSetId" in new BaseScope {
      override def doc1 = factory.document(documentSetId=123L)
      result must /("documents") /#(0) /("documentSetId" -> "123")
    }

    "handle a null page_number" in new BaseScope {
      override def doc1 = factory.document(pageNumber=None)
      result must contain(""""page_number":null""")
    }

    "show page_number=null when there is no fullDocumentInfo" in new BaseScope {
      // This comes up with single-page documents. Our ingest pipeline
      // doesn't know, when it's writing page 1, that page 1 is the final page.
      // So the document gets created with a page number. But users don't want
      // to _see_ the page number because it clutters the UI.
      override def doc1 = factory.document(pageNumber=Some(1))
      // [adamhooper, 2020-04-16] how to match `null` with specs2 JSON matchers?
      // I give up. This is a good reason to switch frameworks.
      result must contain("\"page_number\":null")
    }

    "set node IDs" in new BaseScope {
      override def doc1AndIds = (doc1, None, Vector[Long](5L, 6L, 7L), Vector[Long](), Vector[Utf16Snippet](), None, None)
      result must /("documents") /#(0) /("nodeids") /#(1) /(6)
    }

    "set tag IDs" in new BaseScope {
      override def doc1AndIds = (doc1, None, Vector[Long](), Vector[Long](5L, 6L, 7L), Vector[Utf16Snippet](), None, None)
      result must /("documents") /#(0) /("tagids") /#(1) /(6)
    }

    "set a URL" in new BaseScope {
      override def doc1 = factory.document(url=Some("http://example.org"))
      result must /("documents") /#(0) /("url" -> "http://example.org")
    }

    "show a thumbnail" in new BaseScope {
      override def doc1AndIds = (doc1, Some("https://thumbnail-url"), Vector(), Vector(), Vector(), None, None)

      result must /("documents") /#(0) /("thumbnailUrl" -> "https://thumbnail-url")
    }

    "show a start snippet" in new BaseScope {
      override def doc1AndIds = (doc1.copy(text="This is a start snippet"), None, Vector(), Vector(), Vector(Utf16Snippet(0, 9, Vector(Utf16Highlight(5, 7)))), None, None)

      result must /("documents") /#(0) /("snippet" -> "This <em>is</em> a…")
    }

    "show an end snippet" in new BaseScope {
      override def doc1AndIds = (doc1.copy(text="This is an end snippet"), None, Vector(), Vector(), Vector(Utf16Snippet(8, 22, Vector(Utf16Highlight(11, 14)))), None, None)

      result must /("documents") /#(0) /("snippet" -> "…an <em>end</em> snippet")
    }

    "HTML-escape snippets" in new BaseScope {
      override def doc1AndIds = (doc1.copy(text="1 < <2"), None, Vector(), Vector(), Vector(Utf16Snippet(0, 6, Vector(Utf16Highlight(4, 6)))), None, None)

      result must /("documents") /#(0) /("snippet" -> "1 &lt; <em>&lt;2</em>")
    }

    "set metadata" in new BaseScope {
      override def doc1 = factory.document(metadataJson=Json.obj("foo" -> "bar", "bar" -> "baz"))
      result must /("documents") /#(0) /("metadata") /("foo" -> "bar")
      result must /("documents") /#(0) /("metadata") /("bar" -> "baz")
    }

    "set isFromOcr" in new BaseScope {
      override def doc1 = factory.document(isFromOcr=true)
      result must /("documents") /#(0) /("isFromOcr" -> true)
    }

    "set pdfNotes" in new BaseScope {
      override def doc1 = factory.document(pdfNotes=PdfNoteCollection(Array(
        PdfNote(1, 12.0, 13.0, 14.0, 15.0, "Note One"),
        PdfNote(2, 22.0, 23.0, 24.0, 25.0, "Note Two"),
      )))
      result must /("documents") /#(0) /("pdfNotes") /#(0) /("pageIndex" -> 1)
      result must /("documents") /#(0) /("pdfNotes") /#(0) /("x" -> 12.0)
      result must /("documents") /#(0) /("pdfNotes") /#(0) /("text" -> "Note One")
      result must /("documents") /#(0) /("pdfNotes") /#(1) /("y" -> 23.0)
    }

    "set rootFile" in new BaseScope {
      override def doc1AndIds = (doc1, None.asInstanceOf[Option[String]], Vector[Long](), Vector[Long](), Vector[Utf16Snippet](), Some(factory.file2(id=1234L, filename="file.csv")), None)
      result must /("documents") /#(0) /("rootFile") /("id" -> 1234)
      result must /("documents") /#(0) /("rootFile") /("filename" -> "file.csv")
    }

    "set fullDocumentInfo and page_number" in new BaseScope {
      override def doc1 = factory.document(documentSetId=123L, pageNumber=Some(1))
      override def doc1AndIds = (doc1, None.asInstanceOf[Option[String]], Vector[Long](), Vector[Long](), Vector[Utf16Snippet](), None, Some(FullDocumentInfo(1, 4, 5L)))
      result must /("documents") /#(0) /("page_number" -> 1)
      result must /("documents") /#(0) /("fullDocumentInfo") /("pageNumber" -> 1)
      result must /("documents") /#(0) /("fullDocumentInfo") /("nPages" -> 4)
      result must /("documents") /#(0) /("fullDocumentInfo") /("url" -> s"/documentsets/123/files/5")
    }

    "show page_number=null and fullDocumentInfo=null when fullDocumentInfo.nPages == 1" in new BaseScope {
      // This comes up with single-page documents. Our ingest pipeline
      // doesn't know, when it's writing page 1, that page 1 is the final page.
      // So the document gets created with a page number. But users don't want
      // to _see_ the page number because it clutters the UI.
      override def doc1 = factory.document(pageNumber=Some(1))
      override def doc1AndIds = (doc1, None.asInstanceOf[Option[String]], Vector[Long](), Vector[Long](), Vector[Utf16Snippet](), None, Some(FullDocumentInfo(1, 1, 5L)))
      // [adamhooper, 2020-04-16] how to match `null` with specs2 JSON matchers?
      // I give up. This is a good reason to switch frameworks.
      result must contain("\"page_number\":null")
      result must contain("\"fullDocumentInfo\":null")
    }
  }
}
