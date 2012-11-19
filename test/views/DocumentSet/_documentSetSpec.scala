package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import models.orm.{DocumentSet, DocumentSetCreationJob}
import models.orm.DocumentSetCreationJobState._
import models.orm.DocumentSetType._

class _documentSetSpec extends Specification {
  trait ViewContext extends Scope {
    val documentSet: DocumentSet

    lazy val body = _documentSet(documentSet).body
    lazy val j = jerry(body)
    def $(selector: java.lang.String) = j.$(selector)
  }

  trait NormalDocumentSetContext extends ViewContext {
    override val documentSet = DocumentSet(DocumentCloudDocumentSet, 1, "a title", Some("a query"), providedDocumentCount=Some(20))
  }

  trait DocumentSetWithJobContext extends ViewContext {
    val documentSetId = 1L
    val job : DocumentSetCreationJob
    override lazy val documentSet = DocumentSet(DocumentCloudDocumentSet, documentSetId, title="a title", query=Some("a query"), providedDocumentCount=Some(10), documentSetCreationJob=Some(job))
  }


  class FakeDocumentSetCreationJob(documentSetId: Long, state: DocumentSetCreationJobState,
    fractionComplete: Double = 0.0, override val jobsAheadInQueue: Long = -1l) extends
  DocumentSetCreationJob(documentSetId, state = state, fractionComplete = fractionComplete)
  
  
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
      override val job = new FakeDocumentSetCreationJob(documentSetId, NotStarted) 
      
      $("li.unfinished").length must be_>=(1)
    }

    "should show a progress bar" in new DocumentSetWithJobContext {
      override val job =
	new FakeDocumentSetCreationJob(documentSetId, state=InProgress, fractionComplete=0.2) 
      $("progress").length must be_>=(1)
    }

    "should set the progress bar to the correct percentage" in new DocumentSetWithJobContext {
      override val job =
	new FakeDocumentSetCreationJob(documentSetId, state=InProgress, fractionComplete=0.2)
      $("progress").attr("value") must beEqualTo("20")
    }

    "should show a label for IN_PROGRESS" in new DocumentSetWithJobContext {
      override val job = new FakeDocumentSetCreationJob(documentSetId, state=InProgress)
      $(".state").text() must endWith("IN_PROGRESS")
    }

    "should show a document count when complete" in new NormalDocumentSetContext {
      $("span.document-count").text() must endWith("document_count")
    }

   "should show position in queue for NotStarted jobs" in new DocumentSetWithJobContext {
     override val job = new FakeDocumentSetCreationJob(documentSetId, state=NotStarted, jobsAheadInQueue=1)
      $(".state-description").text.trim must endWith("jobs_to_process")
    }
  }
}
