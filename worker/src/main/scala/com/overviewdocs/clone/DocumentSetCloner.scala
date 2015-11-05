package com.overviewdocs.clone

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.{DocumentSetCreationJob,DocumentSetCreationJobState}
import com.overviewdocs.util.{ DocumentSetCreationJobStateDescription, Logger, SortedDocumentIdsRefresher }
import com.overviewdocs.util.Progress.Progress
import com.overviewdocs.util.DocumentSetCreationJobStateDescription._

object DocumentSetCloner extends HasBlockingDatabase {
  private case object JobWasCancelled extends Throwable
  private lazy val logger = Logger.forClass(getClass)

  /** Updates the document_set_creation_job with the latest progress; throws
    * JobWasCancelled if we are to skip the remaining steps.
    *
    * (Basically, it implements goto.)
    */
  private def reportProgressAndCheckContinue(job: DocumentSetCreationJob, progress: Double): Unit = {
    import database.api._

    logger.info("Job {} cloning {} to {}: {}%% done", job.id, job.sourceDocumentSetId.get, job.documentSetId, progress)
    val cancelled = blockingDatabase.option(sql"""
      UPDATE document_set_creation_job
      SET
        fraction_complete = $progress,
        status_description = 'saving_document_tree'
      WHERE id = ${job.id}
      RETURNING state = ${DocumentSetCreationJobState.Cancelled.id}
    """.as[Boolean]).getOrElse(true)

    if (cancelled) throw JobWasCancelled
  }

  /** Clones the document set and returns.
    *
    * If the job is cancelled partway, returns early.
    */
  def run(job: DocumentSetCreationJob): Unit = {
    val sourceId: Long = job.sourceDocumentSetId.get
    val cloneId: Long = job.documentSetId

    logger.info("Job {} cloning {} to {}: starting", job.id, sourceId, cloneId)

    try {
      DocumentCloner.clone(sourceId, cloneId)
      reportProgressAndCheckContinue(job, 0.10)

      Await.result(DocumentSetIndexer.indexDocuments(cloneId), Duration.Inf)
      reportProgressAndCheckContinue(job, 0.20)

      Await.result(SortedDocumentIdsRefresher.refreshDocumentSet(cloneId), Duration.Inf)
      reportProgressAndCheckContinue(job, 0.30)

      NodeCloner.clone(sourceId, cloneId)
      TreeCloner.clone(sourceId, cloneId)
      reportProgressAndCheckContinue(job, 0.50)

      val tagIdMapping: Map[Long,Long] = TagCloner.clone(sourceId, cloneId)
      reportProgressAndCheckContinue(job, 0.60)

      DocumentTagCloner.clone(sourceId, cloneId, tagIdMapping)
      reportProgressAndCheckContinue(job, 0.70)

      DocumentProcessingErrorCloner.clone(sourceId, cloneId)
      reportProgressAndCheckContinue(job, 0.80)

      NodeDocumentCloner.clone(sourceId, cloneId)
      reportProgressAndCheckContinue(job, 0.99)
    } catch {
      case JobWasCancelled => {}
    }
  }
}
