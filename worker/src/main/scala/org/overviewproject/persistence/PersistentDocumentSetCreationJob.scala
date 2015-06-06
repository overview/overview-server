/*
 * PersistentDocumentSetCreationJob.scala
 *
 * FIXME: replace this with a DocumentSetCreationJobStore
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.postgres.LO
import org.overviewproject.database.DB
import org.overviewproject.database.DeprecatedDatabase

/**
 * Contains attributes of a DocumentSetCreationJob
 * and allows updates and deletions.
 */
trait PersistentDocumentSetCreationJob {

  val id: Long
  val documentSetId: Long

  val jobType: DocumentSetCreationJobType.Value
  val lang: String
  val suppliedStopWords: Option[String]
  val importantWords: Option[String]
  val splitDocuments: Boolean

  // Only some jobs require DocumentCloud credentials
  val documentCloudUsername: Option[String]
  val documentCloudPassword: Option[String]

  // Only CsvUpload jobs require contentsOid
  val contentsOid: Option[Long]

  // Only Clone jobs require sourceDocumentSetId
  val sourceDocumentSetId: Option[Long]

  // Only FileUpload jobs require fileGroupId
  val fileGroupId: Option[Long]
  
  // Only Recluster jobs require tree_title, tree_description or tag_id
  val treeTitle: Option[String]
  val treeDescription: Option[String]
  val tagId: Option[Long]
  
  var retryAttempts: Int 
  
  var state: DocumentSetCreationJobState
  var fractionComplete: Double
  var statusDescription: Option[String]

  def observeCancellation(f: PersistentDocumentSetCreationJob => Unit)

  /**
   * Updates state, fractionComplete, and statusDescription
   */
  def update: Unit

  /** refreshes the job state with value read from the database */
  def checkForCancellation

  /** delete the object from the database */
  def delete: Unit
}

/** Factory for loading jobs from the database */
object PersistentDocumentSetCreationJob {
  import org.overviewproject.persistence.orm.Schema.documentSetCreationJobs
  import org.overviewproject.postgres.SquerylEntrypoint._
  import org.overviewproject.tree.orm.DocumentSetCreationJob
  
  /** Find all jobs in the specified state */
  def findJobsWithState(state: DocumentSetCreationJobState): List[PersistentDocumentSetCreationJob] = {

    val jobs = from(documentSetCreationJobs)(d => where(d.state === state) select (d))
    jobs.map(new PersistentDocumentSetCreationJobImpl(_)).toList
  }

  /** Find first job, ordered by id, in the specified state */
  def findFirstJobWithState(state: DocumentSetCreationJobState): Option[PersistentDocumentSetCreationJob] = {
    val job = from(documentSetCreationJobs)(d => where(d.state === state) select (d) orderBy (d.id)).page(0, 1).headOption

    job.map(new PersistentDocumentSetCreationJobImpl(_))
  }

  private class PersistentDocumentSetCreationJobImpl(documentSetCreationJob: DocumentSetCreationJob)
    extends PersistentDocumentSetCreationJob {
    val id: Long = documentSetCreationJob.id
    val documentSetId: Long = documentSetCreationJob.documentSetId
    val jobType: DocumentSetCreationJobType.Value = documentSetCreationJob.jobType
    val lang: String = documentSetCreationJob.lang
    val suppliedStopWords: Option[String] = Some(documentSetCreationJob.suppliedStopWords)
    val importantWords: Option[String] = Some(documentSetCreationJob.importantWords)
    val documentCloudUsername: Option[String] = documentSetCreationJob.documentcloudUsername
    val documentCloudPassword: Option[String] = documentSetCreationJob.documentcloudPassword
    val splitDocuments: Boolean = documentSetCreationJob.splitDocuments
    val contentsOid: Option[Long] = documentSetCreationJob.contentsOid
    val sourceDocumentSetId: Option[Long] = documentSetCreationJob.sourceDocumentSetId
    val fileGroupId: Option[Long] = documentSetCreationJob.fileGroupId
    val treeTitle: Option[String] = documentSetCreationJob.treeTitle
    val treeDescription: Option[String] = documentSetCreationJob.treeDescription
    val tagId: Option[Long] = documentSetCreationJob.tagId

    var retryAttempts: Int = documentSetCreationJob.retryAttempts
    var state: DocumentSetCreationJobState = documentSetCreationJob.state
    var fractionComplete: Double = documentSetCreationJob.fractionComplete
    var statusDescription: Option[String] = Some(documentSetCreationJob.statusDescription)

    private var cancellationObserver: Option[PersistentDocumentSetCreationJob => Unit] = None

    /** Register a callback for notification if job is cancelled when delete is called  */
    def observeCancellation(f: PersistentDocumentSetCreationJob => Unit) { cancellationObserver = Some(f) }

    /**
     * Updates state, fractionComplete, and statusDescription
     * Does not change the state if job is cancelled
     */
    def update {
      checkForCancellation
      val updatedJob = org.overviewproject.postgres.SquerylEntrypoint.update(documentSetCreationJobs)(d =>
        where(d.id === documentSetCreationJob.id)
          set (d.documentSetId := documentSetId,
            d.retryAttempts := retryAttempts,
            d.state := state.inhibitWhen(d.state == Cancelled),
            d.fractionComplete := fractionComplete,
            d.statusDescription := statusDescription.getOrElse("")))
    }

    def checkForCancellation {
      val job = documentSetCreationJobs.where(dscj => dscj.id === documentSetCreationJob.id).forUpdate.headOption
      for (j <- job; if (j.state == Cancelled)) state = Cancelled
    }

    def delete {
      val lockedJob = from(documentSetCreationJobs)(dscj =>
        where(dscj.id === documentSetCreationJob.id) select (dscj)).forUpdate.singleOption

      lockedJob.map { j =>
        if (j.state == Cancelled) cancellationObserver.map { notify => notify(this) }
        else {
          implicit val pgc = DB.pgConnection(DeprecatedDatabase.currentConnection)
          documentSetCreationJobs.delete(j.id)
          j.contentsOid.map { oid => LO.delete(oid) }
        }
      }
    }
  }
}
