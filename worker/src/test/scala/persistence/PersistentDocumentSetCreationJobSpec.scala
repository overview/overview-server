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
  
  "PersistentDocumentSetCreationJob" should {
    
    "find all submitted jobs" in new DbTestContext {
      val documentSetId = insertDocumentSet("PersistentDocumentSetCreationJobSpec")
      val submitted = Submitted.id
      val inProgress = InProgress.id
      println(submitted + " " + inProgress)
      SQL("""
          INSERT INTO document_set_creation_job (document_set_id, state) VALUES 
            ({documentSetId},{state1}),
            ({documentSetId}, {state2}),
            ({documentSetId}, {state3})
          """).on("documentSetId" -> documentSetId,
                  "state1" -> submitted, "state2" -> submitted, "state3" -> inProgress).
               executeUpdate()
               
      val jobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      jobs.map(_.state).distinct must contain(Submitted).only
      jobs.map(_.documentSetId).distinct must contain(documentSetId).only
    }
    
    "update job state" in new DbTestContext {
      val documentSetId = insertDocumentSet("PersistentDocumentSetCreationJobSpec")
      val submitted = Submitted.id
      val inProgress = InProgress.id
      
      val jobId = SQL(
        """
        INSERT INTO document_set_creation_job (document_set_id, state) VALUES 
          ({documentSetId}, {state})
        """).on("documentSetId" -> documentSetId, "state" -> submitted).executeInsert()
               
      val jobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      jobs.foreach(_.state = InProgress)
      val updates = jobs.map(_.update) 
     
      updates must contain(1l).only
    }
  }
  
  step(shutdownDb)
}