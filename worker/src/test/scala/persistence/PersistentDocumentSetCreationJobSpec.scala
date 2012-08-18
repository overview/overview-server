/*
 * PersistentDocumentSetCreationJobSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import anorm.SqlParser._
import helpers.DbSpecification
import java.sql.Connection
import org.specs2.mutable.Specification
import persistence.DocumentSetCreationJobState._


class PersistentDocumentSetCreationJobSpec extends DbSpecification {

  step(setupDb)
  
  def findAdminUserId(implicit connection: Connection): Option[Long] = {
    SQL("""
        SELECT id FROM "user" WHERE email = 'admin@overview-project.org'
        """).as(long("id") singleOpt)
  }
  
  "PersistentDocumentSetCreationJob" should {
    
    "find all submitted jobs" in new DbTestContext {
      val userId = findAdminUserId.get
      val submitted = Submitted.id
      val inProgress = InProgress.id
      
      SQL("""
          INSERT INTO document_set_creation_job (query, state, user_id) VALUES 
            ('q1', {state1}, {user}),
            ('q2', {state2}, {user}),
            ('q3', {state3}, {user})
          """).on("state1" -> submitted, "state2" -> submitted, 
                  "state3" -> inProgress, "user" -> userId).
               executeUpdate()
               
      val jobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      jobs.map(_.query) must haveTheSameElementsAs(Seq("q1", "q2"))
      jobs.map(_.state).distinct must contain(Submitted).only
      jobs.map(_.userId).distinct must contain(userId).only
    }
    
    "update job state" in new DbTestContext {
      val userId = findAdminUserId.get
      val submitted = Submitted.id
      val inProgress = InProgress.id
      
      val jobId = SQL(
          """
          INSERT INTO document_set_creation_job (query, state, user_id) VALUES 
            ('q1', {state}, {user})
          """).on("state" -> submitted, "user" -> userId).executeInsert()
               
      val jobs = PersistentDocumentSetCreationJob.findAllSubmitted
      
      jobs.map(_.state = InProgress)
      val updates = jobs.map(_.update) 
     
      updates must contain(1l).only
    }
  }
  
  step(shutdownDb)
}