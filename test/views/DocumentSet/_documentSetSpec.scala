package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import models.{ DocumentCloudCredentials, OverviewDocumentSet, OverviewDocumentSetCreationJob }
import models.orm.DocumentSetType._
import helpers.FakeOverviewDocumentSet

class _documentSetSpec extends Specification {

  class FakeDocumentSetCreationJob(val state: DocumentSetCreationJobState,
    val fractionComplete: Double = 0.0, override val jobsAheadInQueue: Int = 0) extends OverviewDocumentSetCreationJob {
    val id = 1l;
    val documentSetId = 1l
    val stateDescription = ""

    def withDocumentCloudCredentials(username: String, password: String): OverviewDocumentSetCreationJob with DocumentCloudCredentials = null
    def withState(newState: DocumentSetCreationJobState) = this
    
    def save = this
  }

  trait ViewContext extends Scope {
    val documentSet: OverviewDocumentSet

    lazy val body = _documentSet(documentSet).body
    lazy val j = jerry(body)
    def $(selector: java.lang.String) = j.$(selector)
  }

  trait NormalDocumentSetContext extends ViewContext {
    val documentSet = FakeOverviewDocumentSet()
  }

  trait DocumentSetWithJobContext extends ViewContext {
    val job: OverviewDocumentSetCreationJob = new FakeDocumentSetCreationJob(InProgress, 0.2, 1)
    val documentSet = FakeOverviewDocumentSet(creationJob = Some(job))
  }
  
  trait DocumentSetWithErrorsContext extends ViewContext {
    val numberOfErrors = 10
    val documentSet = FakeOverviewDocumentSet(errorCount = numberOfErrors)
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
        .filter(href => href.matches(".*/" + documentSet.id + "\\b"))
        .length must be_>=(1)
    }

    "should include a delete button" in new NormalDocumentSetContext {
      $("form.delete").length must be_>=(1)
    }

    "should not have the \"unfinished\" class when finished" in new NormalDocumentSetContext {
      $("li.unfinished").length must beEqualTo(0)
    }
    
    "should have \"unfinished\" class when unfinished" in new DocumentSetWithJobContext {
      $("li.unfinished").length must be_>=(1)
    }

    "should show a progress bar" in new DocumentSetWithJobContext {
      $("progress").length must be_>=(1)
    }

    "should set the progress bar to the correct percentage" in new DocumentSetWithJobContext {
      $("progress").attr("value") must beEqualTo("20")
    }

    "should show a label for IN_PROGRESS" in new DocumentSetWithJobContext {
      $(".state").text() must endWith("IN_PROGRESS")
    }

    "should show a document count when complete" in new NormalDocumentSetContext {
      $("span.document-count").text() must endWith("document_count")
    }

    "should show position in queue for NotStarted jobs" in new DocumentSetWithJobContext {
      $(".state-description").text.trim must endWith("jobs_to_process")
    }
    
    "should not show error count if none exist" in new NormalDocumentSetContext {
      $(".error-list").length must be_==(0)
    }
    
    "should show error count popup if there are errors" in new DocumentSetWithErrorsContext {
      $(".error-list").text.trim must endWith("error_count")
      $(".error-list").attr("href") must be equalTo("/documentsets/1/error-list")
      $(".error-list").attr("data-target") must be equalTo("#error-list-modal")
    }
  }
}
