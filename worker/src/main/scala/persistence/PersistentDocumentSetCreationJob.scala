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
  
  val documentSetId: Long
  var state: DocumentSetCreationJobState
  
  def update(implicit c: Connection) : Long
  def delete(implicit c: Connection) : Long
}

object PersistentDocumentSetCreationJob {
  
  def findAllSubmitted(implicit c: Connection) : List[PersistentDocumentSetCreationJob] = {
    val jobData = 
      SQL("""
          SELECT id, document_set_id, state FROM document_set_creation_job
          WHERE state = {state}
          """).on("state" -> Submitted.id).
            as(long("id") ~ long("document_set_id") ~ int("state") map(flatten) *)
            
            
      jobData.map(new PersistentDocumentSetCreationJobImpl(_))
  }
  
  private class PersistentDocumentSetCreationJobImpl(data: (Long, Long, Int)) 
		  											 
    extends PersistentDocumentSetCreationJob {
    
    val (id, documentSetId, stateNumber) = data
    
    var state = DocumentSetCreationJobState(stateNumber)
   
    def update(implicit c: Connection) : Long = {
      SQL("""
          UPDATE document_set_creation_job SET state = {state}
          WHERE id = {id}
          """).on("state" -> state.id, "id" -> id).executeUpdate()
    }
    
    def delete(implicit c: Connection) : Long = {
      SQL("""
          DELETE FROM document_set_creation_job
          WHERE id = {id}
          """).on("id" -> id).executeUpdate()
          
    }
    
  }
}