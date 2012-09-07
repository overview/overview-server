package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value
  val Submitted, InProgress, Error = Value
}

import DocumentSetCreationJobState._

trait PersistentDocumentSetCreationJob {
  
  val documentSetId: Long
  val documentCloudUsername: Option[String]
  val documentCloudPassword: Option[String]

  var state: DocumentSetCreationJobState
  var fractionComplete: Double
  
  def update(implicit c: Connection) : Long
  def delete(implicit c: Connection) : Long
}

object PersistentDocumentSetCreationJob {
  
  private type DocumentSetCreationJobData = (Long, Long, Int, Double, 
					     Option[String], Option[String])
	  
  def findAllSubmitted(implicit c: Connection) : List[PersistentDocumentSetCreationJob] = {
    val jobData = 
      SQL("""
          SELECT id, document_set_id, state, fraction_complete,
                 documentcloud_username, documentcloud_password
          FROM document_set_creation_job
          WHERE state = {state}
          """).on("state" -> Submitted.id).
            as(long("id") ~ long("document_set_id") ~ int("state") ~ 
               get[Double]("fraction_complete") ~ 
	       get[Option[String]]("documentcloud_username") ~ 
	       get[Option[String]]("documentcloud_password") map(flatten) *)
            
      jobData.map(new PersistentDocumentSetCreationJobImpl(_))
  }
  
  private class PersistentDocumentSetCreationJobImpl(data: DocumentSetCreationJobData)
    extends PersistentDocumentSetCreationJob {
    
    val (id, documentSetId, stateNumber, complete, 
      documentCloudUsername, documentCloudPassword) = data

    var state = DocumentSetCreationJobState(stateNumber)
    var fractionComplete = complete
    
    def update(implicit c: Connection) : Long = {
      SQL("""
          UPDATE document_set_creation_job SET 
          state = {state}, fraction_complete = {fractionComplete}
          WHERE id = {id}
          """).on("state" -> state.id, "fractionComplete" -> fractionComplete,
                  "id" -> id).executeUpdate()
    }
    
    def delete(implicit c: Connection) : Long = {
      SQL("""
          DELETE FROM document_set_creation_job
          WHERE id = {id}
          """).on("id" -> id).executeUpdate()
          
    }
    
  }
}
