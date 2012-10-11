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
  var statusDescription: Option[String]

  def update(implicit c: Connection): Long
  def delete(implicit c: Connection): Long
}

object PersistentDocumentSetCreationJob {

  private type DocumentSetCreationJobData = (Long, // id
    Long, // documentSetId
    Int, // state
    Double, // fractionComplete
    Option[String], // statusDescription
    Option[String], // documentCloudUserName
    Option[String]) // doucmentCloudPassword

  def findJobsWithState(state: DocumentSetCreationJobState)(implicit c: Connection): List[PersistentDocumentSetCreationJob] = {
    val jobData =
      SQL("""
          SELECT id, document_set_id, state, fraction_complete, status_description,
                 documentcloud_username, documentcloud_password
          FROM document_set_creation_job
          WHERE state = {state}
          ORDER BY id
          """).on("state" -> state.id).
        as(long("id") ~ long("document_set_id") ~ int("state") ~
          get[Double]("fraction_complete") ~
          get[Option[String]]("status_description") ~
          get[Option[String]]("documentcloud_username") ~
          get[Option[String]]("documentcloud_password") map (flatten) *)

    jobData.map(new PersistentDocumentSetCreationJobImpl(_))
  }
    
  
  private class PersistentDocumentSetCreationJobImpl(data: DocumentSetCreationJobData)
    extends PersistentDocumentSetCreationJob {

    val (id, documentSetId, stateNumber, complete, status,
      documentCloudUsername, documentCloudPassword) = data

    var statusDescription = status
    var state = DocumentSetCreationJobState(stateNumber)
    var fractionComplete = complete

    def update(implicit c: Connection): Long = {
      SQL("""
          UPDATE document_set_creation_job SET
          state = {state}, fraction_complete = {fractionComplete},
          status_description = {status}
          WHERE id = {id}
          """).on("state" -> state.id, "fractionComplete" -> fractionComplete,
                  "status" -> statusDescription, "id" -> id).executeUpdate()
    }

    def delete(implicit c: Connection): Long = {
      SQL("""
          DELETE FROM document_set_creation_job
          WHERE id = {id}
          """).on("id" -> id).executeUpdate()

    }

  }
}
