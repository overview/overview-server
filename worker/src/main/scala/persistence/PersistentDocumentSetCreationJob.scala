package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value
  val Submitted, InProgress, Complete = Value
}

import DocumentSetCreationJobState._

trait PersistentDocumentSetCreationJob {
  
  val query: String
  var state: DocumentSetCreationJobState
  
  def update(implicit c: Connection) : Long
}

object PersistentDocumentSetCreationJob {
  
  def findAllSubmitted(implicit c: Connection) : List[PersistentDocumentSetCreationJob] = {
    val jobData = 
      SQL("""
          SELECT id, query, state FROM document_set_creation_job
          WHERE state = {state}
          """).on("state" -> Submitted.id).
            as(long("id") ~ str("query") ~ int("state") map(flatten) *)
            
            
      jobData.map(new PersistentDocumentSetCreationJobImpl(_))
  }
  
  private class PersistentDocumentSetCreationJobImpl(data: (Long, String, Int)) 
		  											 
    extends PersistentDocumentSetCreationJob {
    
    val (id, query, stateNumber) = data
    var state = DocumentSetCreationJobState(stateNumber)
   
    def update(implicit c: Connection) : Long = {
      SQL("""
          UPDATE document_set_creation_job SET state = {state}
          WHERE id = {id}
          """).on("state" -> state.id, "id" -> id).executeUpdate()
    }
    
  }
}