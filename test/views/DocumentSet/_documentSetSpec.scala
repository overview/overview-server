package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.test.{FakeApplication,FakeRequest}

import org.overviewproject.tree.orm.DocumentSet
import models.OverviewUser

class _documentSetSpec extends Specification {
  step(start(FakeApplication()))

  trait ViewContext extends Scope with Mockito {
    implicit val request = FakeRequest()
    val documentSet: DocumentSet
    val treeId = 1L
    val user = mock[OverviewUser]
    user.isAdministrator returns false
    
    lazy val body = _documentSet(documentSet, treeId, user).body
    lazy val j = jerry(body)
    def $(selector: java.lang.String) = j.$(selector)
  }

  trait NormalDocumentSetContext extends ViewContext {
    val documentSet = DocumentSet()
  }
  
  trait DocumentSetWithErrorsContext extends ViewContext {
    val numberOfErrors = 10
    val documentSet = DocumentSet(id=1L, documentProcessingErrorCount = numberOfErrors)
  }

  "DocumentSet._documentSet" should {
    "be an <li>" in new NormalDocumentSetContext {
      body must beMatching("""(?s)\A\s*<li.*</li>\s*\z$""".r)
    }

    "should have an id equal to the DocumentSet ID" in new NormalDocumentSetContext {
      $("li:first").attr("id") must equalTo("document-set-" + documentSet.id)
    }

    "should include a link to the DocumentSet" in new NormalDocumentSetContext {
      $("a[href]").get()
        .filter(n => n.hasAttribute("href"))
        .map(n => n.getAttribute("href"))
        .filter(href => href.matches(s".*/${documentSet.id}/trees/$treeId\\b"))
        .length must be_>=(1)
    }

    "should include a delete button" in new NormalDocumentSetContext {
      $("form.delete").length must be_>=(1)
    }

    "should show a document count" in new NormalDocumentSetContext {
      $("span.document-count").text() must equalTo("no documents")
    }

    "should not show error count if none exist" in new NormalDocumentSetContext {
      $(".error-count").length must be_==(0)
    }

    "should show error count popup if there are errors" in new DocumentSetWithErrorsContext {
      $("a.error-count").text.trim must equalTo("10 documents could not be loaded")
      $("a.error-count").attr("href") must be equalTo("/documentsets/1/error-list")
      $("a.error-count").attr("data-target") must be equalTo("#error-list-modal")
    }
  }

  step(stop)
}
