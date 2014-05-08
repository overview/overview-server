package models.orm.stores

import org.squeryl.{ KeyedEntityDef, Query }

import org.overviewproject.database.DB
import org.overviewproject.postgres.LO
import org.overviewproject.tree.orm.{ DocumentSetCreationJob, DocumentSetCreationJobState }
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType.Recluster
import org.overviewproject.tree.orm.stores.BaseStore
import models.OverviewDatabase
import models.orm.Schema

object DocumentSetCreationJobStore extends BaseStore(models.orm.Schema.documentSetCreationJobs) {
  /**
   * Deletes a DocumentSetCreationJob.
   *
   * This only works for a NotStarted or an Error job. If the job is already
   * running, cancellation must be communicated to the worker, via cancel().
   *
   * Because of this complex logic, this method is not public. Use
   * DocumentSetStore to manage a document set's lifecycle. Note, in
   * particular, that DocumentSetStore selects the job FOR UPDATE.
   */
  private[stores] def deletePending(job: DocumentSetCreationJob): Unit = {
    require(job.state == DocumentSetCreationJobState.NotStarted || job.state == DocumentSetCreationJobState.Error)

    import org.overviewproject.postgres.SquerylEntrypoint._

    from(Schema.documentSetCreationJobs)(dscj =>
      where(dscj.id === job.id)
        select (&(lo_unlink(dscj.contentsOid)))).toIterable // toIterable() executes it

    Schema.documentSetCreationJobs.delete(job.id)
  }

  /**
   * Tells the worker to delete a DocumentSetCreationJob and some parts of its
   * associated DocumentSet.
   *
   * This only works for an InProgress or Cancelled job. If the job is not
   * running, use deletePending() instead.
   *
   * Because of this complex logic, this method is not public. Use
   * DocumentSetStore to manage a document set's lifecycle. Note, in
   * particular, that DocumentSetStore selects the job FOR UPDATE.
   */
  private[stores] def cancel(job: DocumentSetCreationJob): Unit = {
    require(job.state == DocumentSetCreationJobState.InProgress || job.state == DocumentSetCreationJobState.Cancelled)

    insertOrUpdate(job.copy(state = DocumentSetCreationJobState.Cancelled))
  }

  /**
   * Finds a cancellable job for the given document set, and sets its state to CANCELLED.
   *
   * There can be at most one import job (DocumentCloud, CsvUpload, Clone, FileUpload) for a given document set.
   * There can be multiple Recluster jobs, where at most 1 is running (NOT_STARTED, IN_PROGRESS) and the rest
   * are stopped (ERROR, CANCELLED). Stopped Recluster jobs are not cancellable - this constraint is enforced by the UI.
   *
   *
   * Only the cancellable job is returned. If there is no cancellable job, None is returned
   *
   * The returned job has the original state (ie. not CANCELLED)
   *
   * The job is locked, so try to finish the transaction quickly.
   *
   * This method is actually a lot simpler than what we had before.
   *
   */
  def findCancellableJobByDocumentSetAndCancel(documentSetId: Long): Option[DocumentSetCreationJob] = {
    import org.overviewproject.postgres.SquerylEntrypoint._

    val runningJobs = from(Schema.documentSetCreationJobs)(dscj =>
      where(dscj.documentSetId === documentSetId and
        ((dscj.jobType <> Recluster) or (dscj.state notIn List(Error, Cancelled))))
        select (dscj)).forUpdate

    val firstJob = runningJobs.headOption // There should be at most one running job

    firstJob.map { j => insertOrUpdate(j.copy(state = Cancelled)) }

    firstJob
  }

  override def delete(query: Query[DocumentSetCreationJob]): Int = {
    throw new AssertionError("Do not delete() a DocumentSetCreationJob; cancel() or deletePending() it.")
  }

  override def delete[K](k: K)(implicit ked: KeyedEntityDef[DocumentSetCreationJob, K]): Unit = {
    throw new AssertionError("Do not delete() a DocumentSetCreationJob; cancel() or deletePending() it.")
  }
}
