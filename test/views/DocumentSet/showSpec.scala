package views.json.DocumentSet

import helpers.DbTestContext
import models.orm.{ DocumentSet, DocumentSetCreationJob }
import models.orm.DocumentSetCreationJobState._
import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json.toJson
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class showSpec extends Specification {

  "DocumentSet view generated Json" should {

    trait DocumentSetContext extends Scope {
      val documentSet: DocumentSet
      lazy val documentSetJson = show(documentSet).toString
    }

    trait CompleteDocumentSetContext extends DocumentSetContext {
      override val documentSet = DocumentSet(1, "a title", "a query", Some(20))
    }

    trait DocumentSetWithJobContext extends DocumentSetContext {
      val job: DocumentSetCreationJob
      override lazy val documentSet = DocumentSet(1, "a title", "a query", Some(20), Some(job))
    }
    
    trait InProgressDocumentSetContext extends DocumentSetWithJobContext {
      val job = DocumentSetCreationJob(1, Some("name"), Some("password"), InProgress,
        .23, "")
    }

    class FakeNotStartedJob(state: DocumentSetCreationJobState, description: String) extends
      DocumentSetCreationJob(1, Some("name"), Some("password"), state, 23, description) {
      override val jobsAheadInQueue = 5l
    }

    trait NotStartedDocumentSetContext extends DocumentSetWithJobContext {
      val job = new FakeNotStartedJob(NotStarted, "description")
    }

    trait OutOfMemoryJob extends DocumentSetWithJobContext {
      val job = new FakeNotStartedJob(InProgress, "out_of_memory")
    }
    
    "contain id, query, and html" in new CompleteDocumentSetContext {
      documentSetJson must /("id" -> documentSet.id)
      documentSetJson must /("query" -> documentSet.query)
      documentSetJson must beMatching(""".*"html":"[^<]*<.*>[^>]*".*""")
      documentSetJson must not contain ("state")
    }

    "show in progress job" in new InProgressDocumentSetContext {
      documentSetJson must /("state" -> "views.DocumentSet._documentSet.job_state.IN_PROGRESS")
      documentSetJson must /("percent_complete" -> job.fractionComplete * 100)
      documentSetJson must /("state_description" -> job.stateDescription)
      documentSetJson must not contain ("n_jobs_ahead_in_queue")
    }

    "show not started job" in new NotStartedDocumentSetContext {
      documentSetJson must /("n_jobs_ahead_in_queue" -> 5)
    }

    "show out of memory error" in new OutOfMemoryJob {
      documentSetJson must /("state_description" -> "views.DocumentSet._documentSet.out_of_memory")
    }
  }

}
