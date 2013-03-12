package models

import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import org.overviewproject.test.Specification
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.postgres.SquerylEntrypoint._
import helpers.DbTestContext
import models.orm.DocumentSetType._
import models.orm.DocumentSet

class OverviewDocumentSetCreationJobSpec extends Specification {
  step(start(FakeApplication()))

  "OverviewDocumentSetCreationJob" should {
    import models.orm.Schema.{ documentSetCreationJobs, documentSets }

    trait DocumentSetContext extends DbTestContext {
      val documentSet = DocumentSet(DocumentCloudDocumentSet, title = "title", query = Some("query"))
      var job: OverviewDocumentSetCreationJob = _

      override def setupWithDb = {
        documentSets.insert(documentSet)
        documentSet.createDocumentSetCreationJob()
        job = OverviewDocumentSetCreationJob.findByDocumentSetId(documentSet.id).head
      }
    }

    trait SavedJobContext extends DocumentSetContext {
      var savedJob: OverviewDocumentSetCreationJob = _

      override def setupWithDb = {
        super.setupWithDb
        savedJob = job.save
      }
    }

    trait MultipleJobContext extends DbTestContext {
      var jobs: Seq[OverviewDocumentSetCreationJob] = _

      override def setupWithDb = {
        val sets = Seq.fill(10)(DocumentSet(DocumentCloudDocumentSet, title = "title", query = Some("query")))
        sets.foreach { d =>
          documentSets.insert(d)
          d.createDocumentSetCreationJob()
        }
        jobs = OverviewDocumentSetCreationJob.all
      }
    }

    trait UpdatedStateContext extends SavedJobContext {
      val fractionComplete = 0.55
      val description = "state description"

      override def setupWithDb = {
        super.setupWithDb
        update(documentSetCreationJobs)(j =>
          where(j.id === savedJob.id)
            set (j.fractionComplete := fractionComplete,
              j.statusDescription := description))
      }
    }

    trait CloneJobContext extends DbTestContext {
      val sourceDocumentSet = DocumentSet(DocumentCloudDocumentSet, query = Some("clone"), isPublic = true)
      val cloneDocumentSet = sourceDocumentSet.copy(isPublic = false)
      var cloneJobId: Long = _

      override def setupWithDb = {
        sourceDocumentSet.save
        cloneDocumentSet.save

        val cloneJob = DocumentSetCreationJob(cloneDocumentSet.id, CloneJob, sourceDocumentSetId = Some(sourceDocumentSet.id))
        documentSetCreationJobs.insert(cloneJob)
        cloneJobId = cloneJob.id
      }
    }

    "create a job with a document set" in new DocumentSetContext {
      job.documentSetId must be equalTo (documentSet.id)
      job.state must be equalTo (NotStarted)
    }

    "save job to database" in new SavedJobContext {
      savedJob.id must not be equalTo(0)
    }

    "list all saved jobs ordered by descending ids" in new MultipleJobContext {
      OverviewDocumentSetCreationJob.all.map(_.id) must be equalTo (jobs.map(_.id).sortBy(-_))
    }

    "find job by document set id" in new SavedJobContext {
      OverviewDocumentSetCreationJob.findByDocumentSetId(documentSet.id) must beSome
    }

    "read state information" in new UpdatedStateContext {
      val updatedJob = OverviewDocumentSetCreationJob.findByDocumentSetId(documentSet.id).get
      updatedJob.fractionComplete must be equalTo (fractionComplete)
      updatedJob.stateDescription must be equalTo (description)
    }

    "report position in queue" in new MultipleJobContext {
      val queuePosition = jobs.sortBy(_.id).map(_.jobsAheadInQueue)

      queuePosition must be equalTo Seq.range(1, jobs.size + 1)
    }

    "create a job with DocumentCloud credentials" in new DocumentSetContext {
      val username = "name"
      val password = "password"

      val jobWithCredentials = job.withDocumentCloudCredentials(username, password)

      jobWithCredentials.username must be equalTo (username)
      jobWithCredentials.password must be equalTo (password)

      val savedJob: OverviewDocumentSetCreationJob = jobWithCredentials.save
      val j = documentSetCreationJobs.lookup(savedJob.id).get

      j.documentcloudUsername must beSome
      j.documentcloudPassword must beSome
    }

    "update state" in new SavedJobContext {
      val cancelledJob = savedJob.withState(Cancelled)
      cancelledJob.state must be equalTo (Cancelled)

      cancelledJob.save
      val jobs = OverviewDocumentSetCreationJob.all

      jobs must have size (1)
      jobs.head.state must be equalTo (Cancelled)
    }

    "do not cancel NotStarted job" in new SavedJobContext {
      val cancelledJob = OverviewDocumentSetCreationJob.cancelJobWithDocumentSetId(documentSet.id)

      cancelledJob must beNone
    }

    "cancel clone jobs with source id" in new CloneJobContext {
      val cancelledCloneJobs: Seq[OverviewDocumentSetCreationJob] = OverviewDocumentSetCreationJob.cancelJobsWithSourceDocumentSetId(sourceDocumentSet.id)

      cancelledCloneJobs must have size (1)
      cancelledCloneJobs.head.id must be equalTo (cloneJobId)
      cancelledCloneJobs.head.state must be equalTo (Cancelled)
    }

  }

  step(stop)
}
