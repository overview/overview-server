package views.json.DocumentList

import java.util.UUID

import com.overviewdocs.searchindex.{Highlight,Snippet}
import org.specs2.matcher.{JsonMatchers,Matcher}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json
import models.pagination.Page
import com.overviewdocs.test.factories.{PodoFactory => factory}

class showSpec extends Specification with JsonMatchers {
  trait BaseScope extends Scope {
    val selectionId = UUID.fromString("933c0b0b-fd89-4ed3-ad4a-731bbb04da43")
    def doc1 = factory.document()
    def doc2 = factory.document()

    def doc1AndIds = (doc1, Seq[Long](), Seq[Long](), Seq[Snippet]())
    def doc2AndIds = (doc2, Seq[Long](), Seq[Long](), Seq[Snippet]())
    def docsAndIds = Seq(doc1AndIds, doc2AndIds)

    def resultPage = Page(docsAndIds)

    def result = show(selectionId, resultPage).toString

    def haveSnippets(snippets: Matcher[String]*): Matcher[String] = {
      /("snippets").andHave(eachOf(snippets: _*))
    }
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

    "set a documentSetId" in new BaseScope {
      override def doc1 = factory.document(documentSetId=123L)
      result must /("documents") /#(0) /("documentSetId" -> "123")
    }

    "handle a null page_number" in new BaseScope {
      override def doc1 = factory.document(pageNumber=None)
      result must contain(""""page_number":null""")
    }

    "handle a page_number" in new BaseScope {
      override def doc1 = factory.document(pageNumber=Some(4))
      result must /("documents") /#(0) /("page_number" -> 4)
    }

    "set node IDs" in new BaseScope {
      override def doc1AndIds = (doc1, Seq[Long](5L, 6L, 7L), Seq[Long](), Seq[Snippet]())
      result must /("documents") /#(0) /("nodeids") /#(1) /(6)
    }

    "set tag IDs" in new BaseScope {
      override def doc1AndIds = (doc1, Seq[Long](), Seq[Long](5L, 6L, 7L), Seq[Snippet]())
      result must /("documents") /#(0) /("tagids") /#(1) /(6)
    }

    "set a URL" in new BaseScope {
      override def doc1 = factory.document(url=Some("http://example.org"))
      result must /("documents") /#(0) /("url" -> "http://example.org")
    }

    "show a start snippet" in new BaseScope {
      override def doc1AndIds = (doc1.copy(text="This is a start snippet"), Seq(), Seq(), Seq(Snippet(0, 10, Seq(Highlight(5, 7)))))

      result must haveSnippets(beEqualTo("This <em>is</em> a…"))
    }

    "show an end snippet" in new BaseScope {
      override def doc1AndIds = (doc1.copy(text="This is an end snippet"), Seq(), Seq(), Seq(Snippet(8, 22, Seq(Highlight(16, 19)))))

      result must haveSnippets(beEqualTo("…an <em>end</em> snippet"))
    }

    "HTML-escape snippets" in new BaseScope {
      override def doc1AndIds = (doc1.copy(text="1 < <2"), Seq(), Seq(), Seq(Snippet(0, 6, Seq(Highlight(4, 6)))))

      result must haveSnippets(beEqualTo("1 &lt; <em>&lt;2</em>"))
    }
  }
}
