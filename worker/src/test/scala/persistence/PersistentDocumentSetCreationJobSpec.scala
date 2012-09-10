/*
 * PersistentDocumentSetCreationJobSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbSpecification
import java.sql.Connection
import org.specs2.mutable.Specification
import persistence.DocumentSetCreationJobState._


class PersistentDocumentSetCreationJobSpec extends DbSpecification {

  step(setupDb)
  
  trait JobSetup extends DbTestContext {
    lazy val documentSetId = insertDocumentSet("PersistentDocumentSetCreationJobSpec")
    lazy val allNotStartedJobs = PersistentDocumentSetCreationJob.findAllSubmitted
    lazy val notStartedJob = allNotStartedJobs.head
    
    def insertJob: Long = 
      SQL("""
          INSERT INTO document_set_creation_job (document_set_id, state)
          VALUES ({documentSetId}, {state})
          """).on("documentSetId" -> documentSetId, "state" -> Submitted.id).
              executeInsert().getOrElse(throw new Exception("failed Insert"))
  }

  trait DocumentCloudJobSetup extends JobSetup {
    val dcUsername = "user@documentcloud.org"
    val dcPassword = "dcPassword"

    def insertDocumentCloudJob: Long = 
      SQL("""
          INSERT INTO document_set_creation_job 
	    (document_set_id, state, documentcloud_username, documentcloud_password)
          VALUES ({documentSetId}, {state}, {userName}, {password})
          """).on("documentSetId" -> documentSetId, "state" -> Submitted.id,
		  "userName" -> dcUsername, "password" -> dcPassword).
              executeInsert().getOrElse(throw new Exception("failed Insert"))
  }
  
  "PersistentDocumentSetCreationJob" should {
    
    "find all submitted jobs" in new JobSetup {
      SQL("""
          INSERT INTO document_set_creation_job (document_set_id, state) VALUES 
            ({documentSetId},{state1}),
            ({documentSetId}, {state2}),
            ({documentSetId}, {state3})
          """).on("documentSetId" -> documentSetId,
                  "state1" -> Submitted.id, "state2" -> Submitted.id, 
                  "state3" -> InProgress.id).
               executeUpdate()
               
      allNotStartedJobs.map(_.state).distinct must contain(Submitted).only
      allNotStartedJobs.map(_.documentSetId).distinct must contain(documentSetId).only
    }
    
    
    "update job state" in new JobSetup {
      insertJob
      notStartedJob.state = InProgress
      notStartedJob.update

      val remainingNotStartedJobs = PersistentDocumentSetCreationJob.findAllSubmitted
      remainingNotStartedJobs must be empty
    }
    
    "update percent complete" in new JobSetup {
      insertJob
      
      notStartedJob.fractionComplete = 0.5
      notStartedJob.update
      val job = PersistentDocumentSetCreationJob.findAllSubmitted.head
      
      job.fractionComplete must be equalTo(0.5)
    }
    
    "delete itself" in new JobSetup {
      insertJob
      
      notStartedJob.delete
      
      val remainingJobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      remainingJobs must be empty
    }

    "have empty username and password if not available" in new JobSetup {
      insertJob

      allNotStartedJobs.head.documentCloudUsername must beNone
      allNotStartedJobs.head.documentCloudPassword must beNone
    }

    "have username and password if available" in new DocumentCloudJobSetup {
      insertDocumentCloudJob

      allNotStartedJobs.head.documentCloudUsername must beSome.like { case n => 
	n must be equalTo(dcUsername) }
    }
  }
  
  step(shutdownDb)
}
