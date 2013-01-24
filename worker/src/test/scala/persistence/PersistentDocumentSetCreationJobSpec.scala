/*
 * PersistentDocumentSetCreationJobSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import org.overviewproject.postgres.SquerylEntrypoint.kedForKeyedEntities
import org.overviewproject.test.DbSetup.insertDocumentSet
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

import org.overviewproject.tree.orm.DocumentSetCreationJobType.DocumentCloudJob

class PersistentDocumentSetCreationJobSpec extends DbSpecification {

  step(setupDb)

  def insertDocumentSetCreationJob(documentSetId: Long, state: DocumentSetCreationJobState): Long = {
    val job = DocumentSetCreationJob(documentSetId, DocumentCloudJob, state = state)
    Schema.documentSetCreationJobs.insertOrUpdate(job).id
  }

  def insertDocumentCloudJob(documentSetId: Long, state: DocumentSetCreationJobState, dcUserName: String, dcPassword: String): Long = {
    val job = DocumentSetCreationJob(documentSetId, DocumentCloudJob, state = state, documentcloudUsername = Some(dcUserName), documentcloudPassword = Some(dcPassword))
    Schema.documentSetCreationJobs.insertOrUpdate(job).id
  }
  
  def insertJobsWithState(documentSetId: Long, states: Seq[DocumentSetCreationJobState]) {
    states.foreach(s => insertDocumentSetCreationJob(documentSetId, s))
  }

  trait DocumentSetContext extends DbTestContext {
    lazy val documentSetId = insertDocumentSet("PersistentDocumentSetCreationJobSpec")
  }
  
  trait JobSetup extends DocumentSetContext {
    var notStartedJob: PersistentDocumentSetCreationJob = _
    var jobId: Long = _
    
    override def setupWithDb = {
      jobId = insertDocumentSetCreationJob(documentSetId, NotStarted)
      notStartedJob = PersistentDocumentSetCreationJob.findJobsWithState(NotStarted).head
    }
  }

  trait JobQueueSetup extends DocumentSetContext {
    override def setupWithDb = {
      insertJobsWithState(documentSetId, Seq(NotStarted, InProgress, NotStarted, InProgress))
    }
  }

  trait DocumentCloudJobSetup extends DocumentSetContext {
    val dcUsername = "user@documentcloud.org"
    val dcPassword = "dcPassword"
    var dcJob: PersistentDocumentSetCreationJob = _
    
    override def setupWithDb = {
      insertDocumentCloudJob(documentSetId, NotStarted, dcUsername, dcPassword)
      dcJob = PersistentDocumentSetCreationJob.findJobsWithState(NotStarted).head
    }
  }
  
  trait CancelledJob extends DocumentSetContext {
    var cancelledJob: PersistentDocumentSetCreationJob = _
    var cancelNotificationReceived: Boolean = false
    
    override def setupWithDb = {
      insertDocumentSetCreationJob(documentSetId, Cancelled)
      cancelledJob = PersistentDocumentSetCreationJob.findJobsWithState(Cancelled).head
      cancelledJob.observeCancellation( j => cancelNotificationReceived = true)
    }
  }

  "PersistentDocumentSetCreationJob" should {

    "find all submitted jobs" in new JobQueueSetup {
      val notStarted = PersistentDocumentSetCreationJob.findJobsWithState(NotStarted)

      notStarted must have size(2)
      notStarted.map(_.state).distinct must contain(NotStarted).only
      notStarted.map(_.documentSetId).distinct must contain(documentSetId).only
    }

    "find all in progress jobs" in new JobQueueSetup {
      val inProgress = PersistentDocumentSetCreationJob.findJobsWithState(InProgress)

      inProgress must have size(2)
      inProgress.map(_.state).distinct must contain(InProgress).only
    }
    
    "update job state" in new JobSetup {
      notStartedJob.state = InProgress
      notStartedJob.update

      val remainingNotStartedJobs = PersistentDocumentSetCreationJob.findJobsWithState(NotStarted)
      remainingNotStartedJobs must be empty
    }

    "update percent complete" in new JobSetup {
      notStartedJob.fractionComplete = 0.5
      notStartedJob.update
      val job = PersistentDocumentSetCreationJob.findJobsWithState(NotStarted).head

      job.fractionComplete must be equalTo (0.5)
    }

    "update job status" in new JobSetup {
      val status = "status message"
      notStartedJob.statusDescription = Some(status)
      notStartedJob.update
      val job = PersistentDocumentSetCreationJob.findJobsWithState(NotStarted).head

      job.statusDescription must beSome
      job.statusDescription.get must be equalTo(status) 
    }
    
    "not update job state if cancelled" in new CancelledJob {
       cancelledJob.state = InProgress
       cancelledJob.update
       PersistentDocumentSetCreationJob.findJobsWithState(InProgress) must be empty
       val numberOfCancelledJobs = PersistentDocumentSetCreationJob.findJobsWithState(Cancelled).length 
       numberOfCancelledJobs must be equalTo(1)
    }

    "delete itself on completion, if not cancelled" in new JobSetup {
      notStartedJob.delete

      val remainingJobs = PersistentDocumentSetCreationJob.findJobsWithState(NotStarted)

      remainingJobs must be empty
    }
    
    "Notify cancellation observer if job is cancelled" in new CancelledJob {
      cancelledJob.delete
      
      cancelNotificationReceived must beTrue
      PersistentDocumentSetCreationJob.findJobsWithState(Cancelled).headOption must beSome
    }

    "have empty username and password if not available" in new JobSetup {
      notStartedJob.documentCloudUsername must beNone
      notStartedJob.documentCloudPassword must beNone
    }

    "have username and password if available" in new DocumentCloudJobSetup {

      dcJob.documentCloudUsername must beSome
      dcJob.documentCloudUsername.get must be equalTo (dcUsername)
    }
    
    "find first job with a state, ordered by id" in new DocumentSetContext {
      val documentSetId2 = insertDocumentSet("DocumentSet2")
      val jobIds = Seq(documentSetId2, documentSetId).map(insertDocumentSetCreationJob(_, NotStarted.id))
     
      val firstNotStartedJob = PersistentDocumentSetCreationJob.findFirstJobWithState(NotStarted)
     
      firstNotStartedJob must beSome
      // test documentSetId since we can't get at job.id directly
      firstNotStartedJob.get.documentSetId must be equalTo(documentSetId2)
    }
  }

  step(shutdownDb)
}
