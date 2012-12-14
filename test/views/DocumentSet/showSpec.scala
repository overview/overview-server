package views.json.DocumentSet

import helpers.DbTestContext
import org.specs2.mock._
import org.specs2.specification.Scope
import play.api.libs.json.Json.toJson
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import org.overviewproject.test.Specification
import org.overviewproject.tree.orm.DocumentSetCreationJob 
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import models.orm.DocumentSet
import models.orm.DocumentSetType._
import models.OverviewDocumentSet
import models.OverviewDocumentSetCreationJob

class showSpec extends Specification {

  "DocumentSet view generated Json" should {

    trait DocumentSetContext extends Scope {
      val ormDocumentSet: DocumentSet = DocumentSet(DocumentCloudDocumentSet, 1, "a title", Some("a query"), providedDocumentCount=Some(20))
      val job: Option[OverviewDocumentSetCreationJob]
      lazy val documentSet: OverviewDocumentSet = OverviewDocumentSet(ormDocumentSet)
      lazy val documentSetJson = show(documentSet, job).toString
    }

    trait CompleteDocumentSetContext extends DocumentSetContext {
      override val job = None
    }

    class FakeJob(docSet: OverviewDocumentSet, jobState: DocumentSetCreationJobState, description: String) extends OverviewDocumentSetCreationJob  {
      val id = 1l
      val documentSetId = 1l
      val state = jobState
      val documentSet = docSet
      val fractionComplete = 23.45
      val stateDescription = description
      override val jobsAheadInQueue = 5
      def withDocumentCloudCredentials(username: String, password: String) = null
      def save = this
    }

    trait InProgressDocumentSetContext extends DocumentSetContext {
      override val job = Some(new FakeJob(documentSet, InProgress, "description"))
    }

    
    trait NotStartedDocumentSetContext extends DocumentSetContext {
      override val job = Some(new FakeJob(documentSet, NotStarted, "description"))
    }

    trait DescriptionWithArgument extends DocumentSetContext {
      override val job = Some(new FakeJob(documentSet, InProgress, "clustering:8"))
    }

    trait EmptyStateDescription extends DocumentSetContext {
      val job = Some(new FakeJob(documentSet, InProgress, ""))
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
      documentSetJson must /("state_description" ->
        ("views.DocumentSet._documentSet.job_state_description." + "clustering"))
    }

    "empty description leads to empty string" in new EmptyStateDescription {
      documentSetJson must /("state_description" -> "")
    }
  }
}
