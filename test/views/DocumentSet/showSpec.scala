package views.json.DocumentSet

import org.specs2.mock._
import org.specs2.specification.Scope
import play.api.libs.json.Json.toJson
import org.overviewproject.test.Specification
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import models.OverviewDocumentSet
import models.OverviewDocumentSetCreationJob

class showSpec extends Specification {

  "DocumentSet view generated Json" should {

    case class FakeOverviewDocumentSet(creationJob: Option[OverviewDocumentSetCreationJob] = None) extends OverviewDocumentSet {
      val id = 1l
      val title = "a title"
      val query = "a query"
      val user = null
      val createdAt = null
      val documentCount = 15
    }
    

    class FakeJob(jobState: DocumentSetCreationJobState, description: String) extends OverviewDocumentSetCreationJob {
      val id = 1l
      val documentSetId = 1l
      val state = jobState
      val fractionComplete = 23.45
      val stateDescription = description
      override val jobsAheadInQueue = 5
      def withDocumentCloudCredentials(username: String, password: String) = null
      def save = this
    }

    trait DocumentSetContext extends Scope {
      val job: Option[OverviewDocumentSetCreationJob]
      lazy val documentSet: OverviewDocumentSet = FakeOverviewDocumentSet(job)
      lazy val documentSetJson = show(documentSet, job).toString
    }

    trait CompleteDocumentSetContext extends DocumentSetContext {
      override val job = None
    }

    trait InProgressDocumentSetContext extends DocumentSetContext {
      override val job = Some(new FakeJob(InProgress, "description"))
    }

    trait NotStartedDocumentSetContext extends DocumentSetContext {
      override val job = Some(new FakeJob(NotStarted, "description"))
    }

    trait DescriptionWithArgument extends DocumentSetContext {
      override val job = Some(new FakeJob(InProgress, "clustering:8"))
    }

    trait EmptyStateDescription extends DocumentSetContext {
      override val job = Some(new FakeJob(InProgress, ""))
    }

    "contain id and html" in new CompleteDocumentSetContext {
      documentSetJson must /("id" -> documentSet.id)
      documentSetJson must beMatching(""".*"html":"[^<]*<.*>[^>]*".*""")
      documentSetJson must not contain ("state")
    }

    "show in progress job" in new InProgressDocumentSetContext {
      documentSetJson must /("state" -> "models.DocumentSetCreationJob.state.IN_PROGRESS")
      documentSetJson must /("percent_complete" -> job.get.fractionComplete * 100)
      documentSetJson must /("state_description" ->
        ("views.DocumentSet._documentSet.job_state_description." + job.get.stateDescription))
      documentSetJson must not contain ("n_jobs_ahead_in_queue")
    }

    "show not started job" in new NotStartedDocumentSetContext {
      documentSetJson must /("n_jobs_ahead_in_queue" -> 5)
    }

    "expand description key with argument" in new DescriptionWithArgument {
      documentSetJson must /("state_description" -> "views.DocumentSet._documentSet.job_state_description.clustering")
    }

    "empty description leads to empty string" in new EmptyStateDescription {
      documentSetJson must /("state_description" -> "")
    }
  }
}
