package views.html.DocumentSet

import jodd.lagarto.dom.jerry.Jerry.jerry
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import models.orm.DocumentSet
import models.{ DocumentCloudCredentials, OverviewDocumentSet, OverviewDocumentSetCreationJob }
import models.orm.DocumentSetType._
import helpers.DbTestContext

class _documentSetSpec extends Specification {
  step(start(FakeApplication()))

  trait ViewContext extends DbTestContext {
    var ormDocumentSet: DocumentSet = _
    var documentSet: OverviewDocumentSet = _

    val job: Option[OverviewDocumentSetCreationJob] = None

    lazy val body = _documentSet(documentSet).body
    lazy val j = jerry(body)
    def $(selector: java.lang.String) = j.$(selector)
  }

  trait NormalDocumentSetContext extends ViewContext {
    override def setupWithDb = {
      ormDocumentSet = DocumentSet(DocumentCloudDocumentSet, title = "a title", query = Some("a query")).save
      documentSet = OverviewDocumentSet(ormDocumentSet)
    }
  }

  trait DocumentSetWithJobContext extends ViewContext {
    val documentSetId = 1L

    override def setupWithDb = {
      ormDocumentSet = DocumentSet(DocumentCloudDocumentSet, title = "a title", query = Some("a query")).save
      documentSet = OverviewDocumentSet(ormDocumentSet)
    }
  }

  class FakeDocumentSetCreationJob(val documentSet: OverviewDocumentSet, val state: DocumentSetCreationJobState,
    val fractionComplete: Double = 0.0, override val jobsAheadInQueue: Int = 0) extends OverviewDocumentSetCreationJob {
    val id = 1l;
    val documentSetId = documentSet.id
    val stateDescription = ""

    def withDocumentCloudCredentials(username: String, password: String): OverviewDocumentSetCreationJob with DocumentCloudCredentials = null
    def save = this
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
      //override val job = Some(new FakeDocumentSetCreationJob(documentSet, NotStarted))
      override val job = Some(OverviewDocumentSetCreationJob(documentSet).save)
      $("li.unfinished").length must be_>=(1)
    }

    "should show a progress bar" in new DocumentSetWithJobContext {
      //override val job = Some(new FakeDocumentSetCreationJob(documentSet, state = InProgress, fractionComplete = 0.2))
      models.orm.Schema.documentSetCreationJobs.insert(DocumentSetCreationJob(documentSet.id, state = InProgress, fractionComplete = 0.2))
      $("progress").length must be_>=(1)
    }

    "should set the progress bar to the correct percentage" in new DocumentSetWithJobContext {
      //override val job = Some(new FakeDocumentSetCreationJob(documentSet, state = InProgress, fractionComplete = 0.2))
      models.orm.Schema.documentSetCreationJobs.insert(DocumentSetCreationJob(documentSet.id, state = InProgress, fractionComplete = 0.2))
      $("progress").attr("value") must beEqualTo("20")
    }

    "should show a label for IN_PROGRESS" in new DocumentSetWithJobContext {
     // override val job = Some(new FakeDocumentSetCreationJob(documentSet, state = InProgress))
      models.orm.Schema.documentSetCreationJobs.insert(DocumentSetCreationJob(documentSet.id, state = InProgress, fractionComplete = 0.2))
      $(".state").text() must endWith("importing")
    }

    "should show a document count when complete" in new NormalDocumentSetContext {
      $("span.document-count").text() must endWith("no documents")
    }

    "should show position in queue for NotStarted jobs" in new DocumentSetWithJobContext {
      //override val job = Some(new FakeDocumentSetCreationJob(documentSet, state = NotStarted, jobsAheadInQueue = 1))
    	override val job = Some(OverviewDocumentSetCreationJob(documentSet).save)
      $(".state-description").text.trim must endWith("Waiting for 1 other jobs to complete before processing can begin")
    }
  }

  step(stop)
}
