/*
 * PersistentDocumentSetCreationJobSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import helpers.{DbSpecification, DbTestContext}
import org.specs2.mutable.Specification
import persistence.DocumentSetCreationJobState._

class PersistentDocumentSetCreationJobSpec extends DbSpecification {

  step(setupDB)
  
  "PersistentDocumentSetCreationJob" should {
    
    "find all submitted jobs" in new DbTestContext {
    
      val submitted = Submitted.id
      val inProgress = InProgress.id
      
      SQL("""
          INSERT INTO document_set_creation_job (query, state) VALUES 
            ('q1', {state1}),
            ('q2', {state2}),
            ('q3', {state3})
          """).on("state1" -> submitted, "state2" -> submitted, "state3" -> inProgress).
               executeUpdate()
               
      val jobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      jobs.map(_.query) must haveTheSameElementsAs(Seq("q1", "q2"))
      jobs.map(_.state) must haveTheSameElementsAs(Seq(Submitted, Submitted))
    }
    
    "update job state" in new DbTestContext {
      val submitted = Submitted.id
      val inProgress = InProgress.id
      
      val jobId = SQL(
          """
          INSERT INTO document_set_creation_job (query, state) VALUES 
            ('q1', {state})
          """).on("state" -> submitted).executeInsert()
               
      val jobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      jobs.map(_.state = InProgress)
      val updates = jobs.map(_.update) 
     
      updates must contain(1l).only
    }
  }
  
  step(shutdownDB)
}