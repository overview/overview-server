package views.json.DocumentList

import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import org.specs2.mutable.Specification
import play.api.libs.json.Json

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.{ResultPage,ResultPageDetails}

class showSpec extends Specification with JsonMatchers {
  trait BaseScope extends Scope {
    def doc1 = Document(documentSetId=1L, id=1L, description="description1")
    def doc2 = Document(documentSetId=1L, id=2L, description="description2")

    def doc1AndIds = (doc1, Seq[Long](), Seq[Long]())
    def doc2AndIds = (doc2, Seq[Long](), Seq[Long]())

    def docsAndIds = Seq(doc1AndIds, doc2AndIds)

    def resultPage = ResultPage(docsAndIds)

    def result = show(resultPage).toString
  }

  "DocumentList view generated Json" should {
    "contain total_items" in new BaseScope {
      // This gave a NullPointerException on 2014-05-20
      //result must /("total_items" -> 2)
      // So we do this instead:
      result must contain(""""total_items":2""")
    }

    "contain documents" in new BaseScope {
      result must /("documents") /#(1) /("id" -> 2)
    }

    "default to empty title" in new BaseScope {
      result must /("documents") /#(0) /("title" -> "")
    }

    "set a title" in new BaseScope {
      override def doc1 = super.doc1.copy(title=Some("aTitle"))
      result must /("documents") /#(0) /("title" -> "aTitle")
    }

    "default to null page_number" in new BaseScope {
      result must /("documents") /#(0) /("page_number" -> null)
    }

    "set a page_number" in new BaseScope {
      override def doc1 = super.doc1.copy(pageNumber=Some(4))
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
      override def doc1 = super.doc1.copy(url=Some("http://example.org"))
      result must /("documents") /#(0) /("url" -> "http://example.org")
    }
  }
}
