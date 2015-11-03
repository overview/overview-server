package views.html.DocumentSet

import com.overviewdocs.models.{DocumentSet,ImportJob}

class _documentSetSpec extends views.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    val documentSet: DocumentSet = factory.documentSet(id=1L)
    val importJobs: Set[ImportJob] = Set()
    val nViews: Int = 3

    def result = _documentSet(documentSet, importJobs, nViews)
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

    "should show the number of views" in new BaseScope {
      override val nViews = 3
      val span = $(".view-count")
      span.text().trim must beEqualTo("views.DocumentSet._documentSet.nViews,3")
    }

    "should show a document count" in new BaseScope {
      override val documentSet = factory.documentSet(id=1L, documentCount=18)
      $("span.document-count").text() must equalTo("views.DocumentSet._documentSet.nDocuments,18")
    }

    "should not show error count if none exist" in new BaseScope {
      override val documentSet = factory.documentSet(id=1L, documentProcessingErrorCount=0)
      $(".error-count").length must be_==(0)
    }

    "should show error count popup if there are errors" in new BaseScope {
      override val documentSet = factory.documentSet(id=1L, documentProcessingErrorCount=10)
      $("a.error-count").text.trim must equalTo("views.DocumentSet._documentSet.nErrors,10")
      $("a.error-count").attr("href") must be equalTo("/documentsets/1/error-list")
      $("a.error-count").attr("data-target") must be equalTo("#error-list-modal")
    }
  }
}
