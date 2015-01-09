package org.overviewproject.util

import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.models.tables.DocumentSetCreationJobs

class JobUpdaterSpec extends SlickSpecification {

  "JobUpdater" should {
    "update job retry parameters" in new JobScope {
      val jobUpdate = job.copy(state = NotStarted, retryAttempts = 5, statusDescription = "updated")
      updater.updateValidJob(jobUpdate)
      
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id)
      
      updatedJob.firstOption must beSome(jobUpdate)
    }
    
    "not update cancelled jobs" in new CancelledJobScope {
      val jobUpdate = job.copy(state = NotStarted, retryAttempts = 5, statusDescription = "updated")
      updater.updateValidJob(jobUpdate)
      
      val updatedJob = DocumentSetCreationJobs.filter(_.id  === job.id)
      
      updatedJob.firstOption must beSome(job)
    }
    

  }

  trait JobScope extends DbScope {
    val updater = new TestJobUpdater
    val documentSet = factory.documentSet()
    val job = factory.documentSetCreationJob(documentSetId = documentSet.id, treeTitle = Some("recluster"), state = jobState)

    def jobState = InProgress

  }
  
  trait CancelledJobScope extends JobScope {
    override def jobState = Cancelled
  }
  
  class TestJobUpdater(implicit val session: Session) extends JobUpdater with SlickClientInSession
}