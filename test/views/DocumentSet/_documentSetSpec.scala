package views.html.DocumentSet

import org.overviewproject.tree.orm.{DocumentSet, Tree}
import models.OverviewUser

class _documentSetSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def documentSet: DocumentSet = DocumentSet()
    def trees : Seq[Tree] = Seq()

    def result = _documentSet(documentSet, trees, fakeUser)
  }

  trait DocumentSetWithErrorsContext extends BaseScope {
    val numberOfErrors = 10
    override def documentSet = DocumentSet(1L, documentProcessingErrorCount = numberOfErrors)
  }

  "DocumentSet._documentSet" should {
    "be an <li>" in new BaseScope {
      html.body must beMatching("""(?s)\A\s*<li.*</li>\s*\z$""".r)
    }

    "should have a data-document-set-id attribute" in new BaseScope {
      $("li:first").attr("data-document-set-id") must equalTo(documentSet.id.toString)
    }

    "should include a link to the DocumentSet" in new BaseScope {
      val mockTree = mock[Tree]
      mockTree.id returns 123L
      override def trees = Seq(mockTree)

      $("a[href]").get()
        .filter(n => n.hasAttribute("href"))
        .map(n => n.getAttribute("href"))
        .filter(href => href.matches(s".*/${documentSet.id}/trees/123\\b"))
        .length must be_>=(1)
    }

    "should include a delete button" in new BaseScope {
      $("form.delete").length must be_>=(1)
    }

    "should show a document count" in new BaseScope {
      $("span.document-count").text() must equalTo("no documents")
    }

    "should not show error count if none exist" in new BaseScope {
      $(".error-count").length must be_==(0)
    }

    "should show error count popup if there are errors" in new DocumentSetWithErrorsContext {
      $("a.error-count").text.trim must equalTo("10 documents could not be loaded")
      $("a.error-count").attr("href") must be equalTo("/documentsets/1/error-list")
      $("a.error-count").attr("data-target") must be equalTo("#error-list-modal")
    }
  }
}
