package views.json.DocumentSet

import helpers.DbTestContext
import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json.toJson
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

import models.orm.{ DocumentSet, DocumentSetCreationJob }
import models.orm.DocumentSetCreationJobState._
import models.orm.DocumentSetType._
import models.OverviewDocumentSet

class showSpec extends Specification {

  "DocumentSet view generated Json" should {

    trait DocumentSetContext extends Scope {
      val ormDocumentSet: DocumentSet
      val documentSet: OverviewDocumentSet = OverviewDocumentSet(ormDocumentSet)
      lazy val documentSetJson = show(documentSet).toString
    }

    trait CompleteDocumentSetContext extends DocumentSetContext {
      override val ormDocumentSet = DocumentSet(DocumentCloudDocumentSet, 1, "a title", Some("a query"), providedDocumentCount=Some(20))
    }

    trait DocumentSetWithJobContext extends DocumentSetContext {
      val job: DocumentSetCreationJob
      override lazy val ormDocumentSet = DocumentSet(DocumentCloudDocumentSet, 1, "a title", Some("a query"), providedDocumentCount=Some(20), documentSetCreationJob=Some(job))
    }

    trait InProgressDocumentSetContext extends DocumentSetWithJobContext {
      val job = DocumentSetCreationJob(1, Some("name"), Some("password"), InProgress,
        .23, "description")
    }

    class FakeNotStartedJob(state: DocumentSetCreationJobState, description: String) extends DocumentSetCreationJob(1, Some("name"), Some("password"), state, 23, description) {
      override val jobsAheadInQueue = 5l
    }

    trait NotStartedDocumentSetContext extends DocumentSetWithJobContext {
      val job = new FakeNotStartedJob(NotStarted, "description")
    }

    trait DescriptionWithArgument extends DocumentSetWithJobContext {
      val job = new FakeNotStartedJob(InProgress, "clustering:8")
    }

    trait EmptyStateDescription extends DocumentSetWithJobContext {
      val job = new FakeNotStartedJob(InProgress, "")
    }

    "contain id and html" in new CompleteDocumentSetContext {
      documentSetJson must /("id" -> documentSet.id)
      documentSetJson must beMatching(""".*"html":"[^<]*<.*>[^>]*".*""")
      documentSetJson must not contain ("state")
    }

    "show in progress job" in new InProgressDocumentSetContext {
      documentSetJson must /("state" -> "models.DocumentSetCreationJob.state.IN_PROGRESS")
      documentSetJson must /("percent_complete" -> job.fractionComplete * 100)
      documentSetJson must /("state_description" ->
        ("views.DocumentSet._documentSet.job_state_description." + job.stateDescription))
      documentSetJson must not contain ("n_jobs_ahead_in_queue")
    }

    "show not started job" in new NotStartedDocumentSetContext {
      documentSetJson must /("n_jobs_ahead_in_queue" -> 5)
    }

    "expand description key with argument" in new DescriptionWithArgument {
      documentSetJson must /("state_description" ->
        ("views.DocumentSet._documentSet.job_state_description." + "clustering"))
    }

    "empty description leads to empty string" in new EmptyStateDescription {
      documentSetJson must /("state_description" -> "")
    }
  }

}
