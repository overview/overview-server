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
               
      val jobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      jobs.map(_.state).distinct must contain(Submitted).only
      jobs.map(_.documentSetId).distinct must contain(documentSetId).only
    }
    
    "update job state" in new JobSetup {
      SQL("""
          INSERT INTO document_set_creation_job (document_set_id, state)
          VALUES ({documentSetId}, {state})
          """).on("documentSetId" -> documentSetId, "state" -> Submitted.id).
              executeInsert()
      
      val jobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      jobs.foreach(_.state = InProgress)
      val updates = jobs.map(_.update) 
     
      updates must contain(1l).only
    }
    
    "delete itself" in new JobSetup {
      SQL("""
          INSERT INTO document_set_creation_job (document_set_id, state)
          VALUES ({documentSetId}, {state})
          """).on("documentSetId" -> documentSetId, "state" -> Submitted.id).
              executeInsert()

      val job = PersistentDocumentSetCreationJob.findAllSubmitted.head
      
      job.delete
      
      val remainingJobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      remainingJobs must be empty
    }
  }
  
  step(shutdownDb)
}