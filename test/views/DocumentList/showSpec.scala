package views.json.DocumentList

import java.util.UUID
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

import models.pagination.Page
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class showSpec extends Specification with JsonMatchers {
  trait BaseScope extends Scope {
    val selectionId = UUID.fromString("933c0b0b-fd89-4ed3-ad4a-731bbb04da43")
    def doc1 = factory.document()
    def doc2 = factory.document()

    def doc1AndIds = (doc1, Seq[Long](), Seq[Long]())
    def doc2AndIds = (doc2, Seq[Long](), Seq[Long]())

    def docsAndIds = Seq(doc1AndIds, doc2AndIds)

    def resultPage = Page(docsAndIds)

    def result = show(selectionId, resultPage).toString
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

    "contain documents" in new BaseScope {
      override def doc2 = factory.document(id=2L)
      result must /("documents") /#(1) /("id" -> 2)
    }

    "set a title" in new BaseScope {
      override def doc1 = factory.document(title="aTitle")
      result must /("documents") /#(0) /("title" -> "aTitle")
    }

    "handle a null page_number" in new BaseScope {
      override def doc1 = factory.document(pageNumber=None)
      result must /("documents") /#(0) /("page_number" -> null)
    }

    "handle a page_number" in new BaseScope {
      override def doc1 = factory.document(pageNumber=Some(4))
      result must /("documents") /#(0) /("page_number" -> 4)
    }

    "set node IDs" in new BaseScope {
      override def doc1AndIds = (doc1, Seq[Long](5L, 6L, 7L), Seq[Long]())
      result must /("documents") /#(0) /("nodeids") /#(1) /(6)
    }

    "set tag IDs" in new BaseScope {
      override def doc1AndIds = (doc1, Seq[Long](), Seq[Long](5L, 6L, 7L))
      result must /("documents") /#(0) /("tagids") /#(1) /(6)
    }

    "set a URL" in new BaseScope {
      override def doc1 = factory.document(url=Some("http://example.org"))
      result must /("documents") /#(0) /("url" -> "http://example.org")
    }
  }
}
