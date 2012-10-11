/*
 * PersistentDocumentSetCreationJob.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import anorm.SqlParser._
import java.sql.Connection

/** Possible states of a DocumentSetCreationJob */
object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value
  val Submitted, InProgress, Error = Value // order matters, must correspond to: 0, 1, 2
}

import DocumentSetCreationJobState._

/**
 * Contains attributes of a DocumentSetCreationJob
 * and allows updates and deletions.
 */ 
trait PersistentDocumentSetCreationJob {

  val documentSetId: Long

  // Only some jobs require DocumentCloud credentials
  val documentCloudUsername: Option[String]
  val documentCloudPassword: Option[String]

  var state: DocumentSetCreationJobState
  var fractionComplete: Double
  var statusDescription: Option[String] 

  /**
   * Updates state, fractionComplete, and statusDescription
   * @return 1 on success, 0 otherwise
   */
  def update(implicit c: Connection): Long

  /** @return 1 on successful deletion, 0 otherwise */
  def delete(implicit c: Connection): Long
}


/** Factory for loading jobs from the database */
object PersistentDocumentSetCreationJob {

  private type DocumentSetCreationJobData = (
    Long, // id
    Long, // documentSetId
    Int, // state
    Double, // fractionComplete
    Option[String], // statusDescription
    Option[String], // documentCloudUserName
    Option[String]) // doucmentCloudPassword

  /** Find all jobs in the specified state */
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
    
  /** Private implementation or PersistentDocumentSet created from database data */
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
