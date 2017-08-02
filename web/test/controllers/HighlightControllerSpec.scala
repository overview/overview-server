package controllers

import org.specs2.specification.Scope
import scala.concurrent.Future

import controllers.backend.{DocumentBackend,HighlightBackend}
import com.overviewdocs.query.{Field,PhraseQuery,Query}
import com.overviewdocs.searchindex.Utf16Highlight
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class HighlightControllerSpec extends ControllerSpecification {
  trait HighlightScope extends Scope {
    val mockDocumentBackend = smartMock[DocumentBackend]
    val mockHighlightBackend = smartMock[HighlightBackend]

    val controller = new HighlightController(mockDocumentBackend, mockHighlightBackend, fakeControllerComponents)

    def foundHighlights: Seq[Utf16Highlight] = Seq()
    def foundText: String = "This is the document text"

    mockHighlightBackend.highlight(any[Long], any[Long], any[Query]) returns Future { foundHighlights }
    mockDocumentBackend.show(any[Long], any[Long]) returns Future { Some(factory.document(text=foundText)) }
  }

  "#index" should {
    trait IndexScope extends HighlightScope {
      val request = fakeAuthorizedRequest
      val q = "foo"
      lazy val result = controller.index(1L, 2L, q)(request)
    }

    "return a JSON response with Highlights" in new IndexScope {
      override def foundHighlights = Seq(Utf16Highlight(2, 4), Utf16Highlight(6, 8))
      h.status(result) must beEqualTo(h.OK)
      h.contentAsString(result) must beEqualTo("[[2,4],[6,8]]")
    }

    "return UTF-16 offsets" in new IndexScope {
      override def foundText = "café latte"
      override def foundHighlights = Seq(Utf16Highlight(0, 4), Utf16Highlight(5, 10))
      h.status(result) must beEqualTo(h.OK)
      h.contentAsString(result) must beEqualTo("[[0,4],[5,10]]")
    }

    "return an empty response" in new IndexScope {
      override def foundHighlights = Seq()
      h.status(result) must beEqualTo(h.OK)
      h.contentAsString(result) must beEqualTo("[]")
    }

    "call HighlightBackend.index" in new IndexScope {
      h.status(result)
      there was one(mockHighlightBackend).highlight(1L, 2L, PhraseQuery(Field.All, "foo"))
    }

    "return BadRequest when the query string is bad" in new IndexScope {
      override val q = "(foo AND )"
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }
  }
}
