package views.html.DocumentSet

import org.overviewproject.tree.orm.{DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tree}
import org.overviewproject.tree.DocumentSetCreationJobType
import models.OverviewUser

class _documentSetSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def documentSet: DocumentSet = DocumentSet(id=1L)
    def nVizs: Int = 3
    def nJobs: Int = 0

    def result = _documentSet(documentSet, nVizs, nJobs, fakeUser)
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

    "should show an open link" in new BaseScope {
      val a = $("div.open a")
      a.get().headOption.map(_.getAttribute("href")) must beSome(s"/documentsets/${documentSet.id}")
    }

    "should show the number of vizs" in new BaseScope {
      val span = $(".viz-count")
      span.attr("data-n-vizs") must beEqualTo(nVizs.toString)
      span.attr("data-n-viz-jobs") must beEqualTo(nJobs.toString)
      span.text().trim must beEqualTo("3 trees")
    }

    "should show viz jobs" in new BaseScope {
      override def nJobs = 1
      val span = $(".viz-count")
      span.attr("data-n-vizs") must beEqualTo(nVizs.toString)
      span.attr("data-n-viz-jobs") must beEqualTo(nJobs.toString)
      span.text().trim.replace("\n", "").replaceAll("\\s+", " ") must beEqualTo("3 trees (clustering another…)")
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
