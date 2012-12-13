package models

import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import org.overviewproject.test.Specification
import helpers.DbTestContext
import models.orm.DocumentSetType._
import models.orm.DocumentSet
import models.orm.DocumentSetCreationJobState._

class OverviewDocumentSetCreationJobSpec extends Specification {
  step(start(FakeApplication()))

  "OverviewDocumentSetCreationJob" should {
    import models.orm.Schema.documentSets

    trait DocumentSetContext extends DbTestContext {
      val documentSet = DocumentSet(DocumentCloudDocumentSet, title = "title", query = Some("query"))
      var job: OverviewDocumentSetCreationJob = _

      override def setupWithDb = {
        documentSets.insert(documentSet)
        job = OverviewDocumentSetCreationJob(OverviewDocumentSet(documentSet))
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

    "save job to database" in new DocumentSetContext {
      val savedJob = job.save

      savedJob.id must not be equalTo(0)
    }
    
    "list all saved jobs ordered by ascending ids" in new MultipleJobs {
      OverviewDocumentSetCreationJob.all must be equalTo(jobs.sortBy(_.id))
    }
  }

  step(stop)
}