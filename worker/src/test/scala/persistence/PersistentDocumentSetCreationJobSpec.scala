/*
 * PersistentDocumentSetCreationJobSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import anorm.SqlParser._
import org.overviewproject.test.DbSpecification
import java.sql.Connection
import org.specs2.mutable.Specification
import persistence.DocumentSetCreationJobState._
import testutil.DbSetup._

class PersistentDocumentSetCreationJobSpec extends DbSpecification {

  step(setupDb)

  def insertDocumentSetCreationJob(documentSetId: Long, state: Int)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document_set_creation_job (document_set_id, state)
        VALUES ({documentSetId}, {state})
        """).on("documentSetId" -> documentSetId, "state" -> state).
      executeInsert().getOrElse(throw new Exception("failed Insert"))
  }

  def insertDocumentCloudJob(documentSetId: Long, state: Int, dcUserName: String, dcPassword: String)(implicit c: Connection): Long = {
    SQL("""
        INSERT INTO document_set_creation_job (document_set_id, state,
          documentcloud_username, documentcloud_password)
        VALUES ({documentSetId}, {state}, {name}, {password})
        """).on("documentSetId" -> documentSetId, "state" -> state, 
	        "name" -> dcUserName, "password" -> dcPassword).
    executeInsert().getOrElse(throw new Exception("failed Insert"))
  }
  
  def insertJobsWithState(documentSetId: Long, states: Seq[Int])(implicit c: Connection) {
    states.foreach(s => insertDocumentSetCreationJob(documentSetId, s))
  }

  trait DocumentSetContext extends DbTestContext {
    lazy val documentSetId = insertDocumentSet("PersistentDocumentSetCreationJobSpec")
  }
  
  trait JobSetup extends DocumentSetContext {
    var notStartedJob: PersistentDocumentSetCreationJob = _
    var jobId: Long = _
    
    override def setupWithDb = {
      jobId = insertDocumentSetCreationJob(documentSetId, Submitted.id)
      notStartedJob = PersistentDocumentSetCreationJob.findJobsWithState(Submitted).head
    }
  }

  trait JobQueueSetup extends DocumentSetContext {
    override def setupWithDb = {
      insertJobsWithState(documentSetId, Seq(Submitted.id, InProgress.id, Submitted.id, InProgress.id))
    }
  }

  trait DocumentCloudJobSetup extends DocumentSetContext {
    val dcUsername = "user@documentcloud.org"
    val dcPassword = "dcPassword"
    var dcJob: PersistentDocumentSetCreationJob = _
    
    override def setupWithDb = {
      insertDocumentCloudJob(documentSetId, Submitted.id, dcUsername, dcPassword)
      dcJob = PersistentDocumentSetCreationJob.findJobsWithState(Submitted).head
    }
  }

  "PersistentDocumentSetCreationJob" should {

    "find all submitted jobs" in new JobQueueSetup {
      val notStarted = PersistentDocumentSetCreationJob.findJobsWithState(Submitted)

      notStarted must have size(2)
      notStarted.map(_.state).distinct must contain(Submitted).only
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

      val remainingNotStartedJobs = PersistentDocumentSetCreationJob.findJobsWithState(Submitted)
      remainingNotStartedJobs must be empty
    }

    "update percent complete" in new JobSetup {
      notStartedJob.fractionComplete = 0.5
      notStartedJob.update
      val job = PersistentDocumentSetCreationJob.findJobsWithState(Submitted).head

      job.fractionComplete must be equalTo (0.5)
    }

    "update job status" in new JobSetup {
      val status = "status message"
      notStartedJob.statusDescription = Some(status)
      notStartedJob.update
      val job = PersistentDocumentSetCreationJob.findJobsWithState(Submitted).head

      job.statusDescription must beSome.like { case s => s must be equalTo(status) }
    }

    "delete itself" in new JobSetup {
      notStartedJob.delete

      val remainingJobs = PersistentDocumentSetCreationJob.findJobsWithState(Submitted)

      remainingJobs must be empty
    }

    "have empty username and password if not available" in new JobSetup {
      notStartedJob.documentCloudUsername must beNone
      notStartedJob.documentCloudPassword must beNone
    }

    "have username and password if available" in new DocumentCloudJobSetup {

      dcJob.documentCloudUsername must beSome.like {
        case n =>
          n must be equalTo (dcUsername)
      }
    }
  }

  step(shutdownDb)
}
