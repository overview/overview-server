package models

import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import org.overviewproject.test.Specification
import org.squeryl.PrimitiveTypeMode._
import helpers.DbTestContext
import models.orm.DocumentSetType._
import models.orm.DocumentSet
import models.orm.DocumentSetCreationJobState._

class OverviewDocumentSetCreationJobSpec extends Specification {
  step(start(FakeApplication()))

  "OverviewDocumentSetCreationJob" should {
    import models.orm.Schema.{ documentSetCreationJobs, documentSets }

    trait DocumentSetContext extends DbTestContext {
      val documentSet = DocumentSet(DocumentCloudDocumentSet, title = "title", query = Some("query"))
      var job: OverviewDocumentSetCreationJob = _

      override def setupWithDb = {
        documentSets.insert(documentSet)
        job = OverviewDocumentSetCreationJob(OverviewDocumentSet(documentSet))
      }
    }
    
    trait SavedJobContext extends DocumentSetContext {
      var savedJob: OverviewDocumentSetCreationJob = _
      
      override def setupWithDb = {
        super.setupWithDb
        savedJob = job.save
      }
    }

    trait MultipleJobs extends DbTestContext {
      var jobs: Seq[OverviewDocumentSetCreationJob] = _

      override def setupWithDb = {
        val sets = Seq.fill(10)(DocumentSet(DocumentCloudDocumentSet, title = "title", query = Some("query")))
        jobs = sets.map { d =>
          documentSets.insert(d)
          OverviewDocumentSetCreationJob(OverviewDocumentSet(d)).save
        }

      }
    }

    "create a job with a document set" in new DocumentSetContext {
      job.documentSetId must be equalTo (documentSet.id)
      job.state must be equalTo (NotStarted)
    }

    "save job to database" in new SavedJobContext {
      savedJob.id must not be equalTo(0)
    }

    "list all saved jobs ordered by ascending ids" in new MultipleJobs {
      OverviewDocumentSetCreationJob.all must be equalTo (jobs.sortBy(_.id))
    }

    "find job by document set id" in new SavedJobContext {
      OverviewDocumentSetCreationJob.findByDocumentSetId(documentSet.id) must beSome
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
  }

  step(stop)
}