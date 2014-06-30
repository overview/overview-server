package views.html.DocumentSet

import org.overviewproject.tree.orm.{DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tree}
import org.overviewproject.tree.DocumentSetCreationJobType
import models.OverviewUser

class _documentSetSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def documentSet: DocumentSet = DocumentSet(id=1L)
    def nTrees: Int = 3

    def result = _documentSet(documentSet, nTrees, fakeUser)

    def fakeTree(documentSetId: Long, id: Long) = Tree(
      id=id,
      documentSetId=documentSetId,
      jobId=0L,
      title="title",
      documentCount=10,
      lang="en"
    )
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

    "should link to show from the h3" in new BaseScope {
      val href = $("h3 a[href]").get().headOption.map(_.getAttribute("href"))
      href must beSome(s"/documentsets/${documentSet.id}")
    }

    "should show the number of trees" in new BaseScope {
      val a = $("div.trees a")
      a.get().headOption.map(_.getAttribute("href")) must beSome(s"/documentsets/${documentSet.id}")
      a.text() must beEqualTo("3 trees")
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
