package views.html.DocumentSet

import org.overviewproject.tree.orm.DocumentSetCreationJob

class _treeErrorJobSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def job: DocumentSetCreationJob = {
      val j = mock[DocumentSetCreationJob]
      j.documentSetId returns 1
      j.id returns 2
      j.treeTitle returns Some("tree title")
      j.statusDescription returns "worker_error"
      j
    }

    override def result = _treeErrorJob(job)
  }

  "views.html.DocumentSet._treeErrorJob" should {
    "have class=error" in new BaseScope {
      Option($("li").attr("class")) must beSome("error")
    }

    "set data-job-id" in new BaseScope {
      Option($("li").attr("data-job-id")) must beSome("2")
    }

    "display the title" in new BaseScope {
      Option($("li h6").text()) must beSome((s: String) => s must contain("tree title"))
    }
  }
}
