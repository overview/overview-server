package models

import play.api.Play.{start, stop}
import play.api.test.FakeApplication
import org.overviewproject.test.Specification
import helpers.DbTestContext
import models.orm.DocumentSetType._
import models.orm.DocumentSet
import models.orm.DocumentSetCreationJobState._

class OverviewDocumentSetCreationJobSpec extends Specification {
  step(start(FakeApplication()))
  
  "OverviewDocumentSetCreationJob" should {
    
    "create a job with a document set" in new DbTestContext {
      import models.orm.Schema.documentSets
      
      val documentSet = DocumentSet(DocumentCloudDocumentSet, title = "title", query = Some("query"))
      documentSets.insert(documentSet)
      
      val job = OverviewDocumentSetCreationJob(OverviewDocumentSet(documentSet))
      
      job.documentSetId must be equalTo(documentSet.id)
      job.state must be equalTo(NotStarted)
      
    }
  }

  step(stop)
}