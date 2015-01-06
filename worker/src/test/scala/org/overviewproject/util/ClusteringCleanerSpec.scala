package org.overviewproject.util

import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.tables.DocumentSetCreationJobs
import org.overviewproject.database.Slick.simple._

class ClusteringCleanerSpec extends SlickSpecification {

  "ClusteringCleaner" should {
    
    "update job retry parameters" in new JobScope {
      val jobUpdate = job.copy(state = NotStarted, retryAttempts = 5, statusDescription = "updated")
      cleaner.updateValidJob(jobUpdate)
      
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id)
      
      updatedJob.firstOption must beSome(jobUpdate)
    }
    
    "not update cancelled jobs" in new CancelledJobScope {
      val jobUpdate = job.copy(state = NotStarted, retryAttempts = 5, statusDescription = "updated")
      cleaner.updateValidJob(jobUpdate)
      
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id)
      
      updatedJob.firstOption must beSome(job)
    }
    
    "delete nodes" in {
      todo
    }
    
    "delete DocumentSetCreationJobNode" in {
      todo
    }
    
    "delete job" in {
      todo
    }
  }
  
  trait JobScope extends DbScope {
    val cleaner = new TestClusteringCleaner
    val documentSet = factory.documentSet()
    val job = factory.documentSetCreationJob(documentSetId = documentSet.id, treeTitle = Some("recluster"), state = jobState)
    
    def jobState = InProgress
  }
  
  trait CancelledJobScope extends JobScope {
    override def jobState = Cancelled
  }
  
  class TestClusteringCleaner(implicit val session: Session) extends ClusteringCleaner with SlickClientInSession
}