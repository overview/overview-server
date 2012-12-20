package models

import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import org.overviewproject.test.Specification
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.squeryl.PrimitiveTypeMode._
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

    trait MultipleJobContext extends DbTestContext {
      var jobs: Seq[OverviewDocumentSetCreationJob] = _

      override def setupWithDb = {
        val sets = Seq.fill(10)(DocumentSet(DocumentCloudDocumentSet, title = "title", query = Some("query")))
        jobs = sets.map { d =>
          documentSets.insert(d)
          OverviewDocumentSetCreationJob(OverviewDocumentSet(d)).save
        }
      }
    }
    
    trait UpdatedStateContext extends SavedJobContext {
      val fractionComplete = 0.55
      val description = "state description"
        
      override def setupWithDb = {
        super.setupWithDb
        update(documentSetCreationJobs)(j =>
          where(j.id === savedJob.id)
          set( j.fractionComplete := fractionComplete,
               j.statusDescription := description)
        )
      }
    }

    "create a job with a document set" in new DocumentSetContext {
      job.documentSetId must be equalTo (documentSet.id)
      job.state must be equalTo (NotStarted)
    }

    "save job to database" in new SavedJobContext {
      savedJob.id must not be equalTo(0)
    }

    "list all saved jobs ordered by ascending ids" in new MultipleJobContext {
      OverviewDocumentSetCreationJob.all must be equalTo (jobs.sortBy(_.id))
    }

    "find job by document set id" in new SavedJobContext {
      OverviewDocumentSetCreationJob.findByDocumentSetId(documentSet.id) must beSome
    }
    
    "read state information" in new UpdatedStateContext {
      val updatedJob = OverviewDocumentSetCreationJob.findByDocumentSetId(documentSet.id).get
      updatedJob.fractionComplete must be equalTo(fractionComplete)
      updatedJob.stateDescription must be equalTo(description)
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
      cancelledJob.state must be equalTo(Cancelled)

      cancelledJob.save
      val jobs = OverviewDocumentSetCreationJob.all
      
      jobs must have size(1)
      jobs.head.state must be equalTo(Cancelled)
    }
    
    "do not cancel NotStarted job" in new SavedJobContext {
      val cancelledJob = OverviewDocumentSetCreationJob.cancelJobWithDocumentSetId(documentSet.id)
      
      cancelledJob must beNone
    }
    
    
    
  }

  step(stop)
}