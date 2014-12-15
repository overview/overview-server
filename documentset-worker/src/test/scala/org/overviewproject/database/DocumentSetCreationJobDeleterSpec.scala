package org.overviewproject.database

import org.overviewproject.test.SlickSpecification
import org.overviewproject.database.Slick.simple._
import org.overviewproject.test.SlickClientInSession
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobType._
import org.overviewproject.models.tables.DocumentSetCreationJobs

class DocumentSetCreationJobDeleterSpec extends SlickSpecification {

  "DocumentSetCreationJobDeleter" should {
    
    "delete a job" in new JobScope {
     await { deleter.deleteByDocumentSet(documentSet.id) }
      
      DocumentSetCreationJobs.list must beEmpty
    }
    
    
  }
  
  
  trait JobScope extends DbScope {
    val deleter = new TestDocumentSetDeleter
    
    val documentSet = factory.documentSet()   
    val job = createJob

    def createJob: DocumentSetCreationJob = 
      factory.documentSetCreationJob(documentSetId = documentSet.id, jobType = Recluster, treeTitle = Some("tree"))
  }
  
  class TestDocumentSetDeleter(implicit val session: Session) extends DocumentSetCreationJobDeleter with SlickClientInSession
  
}