package org.overviewproject.tree.orm

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._
import org.overviewproject.tree.orm.DocumentSetCreationJobType._
import org.overviewproject.postgres.SquerylEntrypoint._

class DocumentSetCreationJobSpec extends DbSpecification {

  step(setupDb)
  
  "DocumentSetCreationJob" should {
    
    "set job types" in new DbTestContext {
      val documentSetId = insertDocumentSet("DocumentSetCreationJob")
 
      val jobTypes = Seq(DocumentCloudJob, CsvImportJob, CloneJob)
      val dcJob = DocumentSetCreationJob(documentSetId, documentSetCreationJobType = DocumentCloudJob)
      val csvJob = DocumentSetCreationJob(documentSetId, documentSetCreationJobType = CsvImportJob)
      val cloneJob = DocumentSetCreationJob(documentSetId, documentSetCreationJobType = CloneJob)
      
      val jobs = Seq(dcJob, csvJob, cloneJob)
      Schema.documentSetCreationJobs.insert(jobs)
      
      val foundJobs = Schema.documentSetCreationJobs.allRows
      
      foundJobs.map(_.documentSetCreationJobType.value) must haveTheSameElementsAs(jobTypes.map(_.value))
    }
  }
  
  step(shutdownDb)
}