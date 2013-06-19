package models.orm.stores

import org.squeryl.{ KeyedEntityDef, Query }

import org.overviewproject.database.DB
import org.overviewproject.postgres.LO
import org.overviewproject.tree.orm.{ DocumentSetCreationJob, DocumentSetCreationJobState }
import org.overviewproject.tree.orm.stores.BaseStore
import models.OverviewDatabase
import models.orm.Schema

object DocumentSetCreationJobStore extends BaseStore(models.orm.Schema.documentSetCreationJobs) {
  /** Deletes a DocumentSetCreationJob.
    *
    * This only works for a NotStarted or an Error job. If the job is already
    * running, cancellation must be communicated to the worker, via cancel().
    *
    * Because of this complex logic, this method is not public. Use
    * DocumentSetStore to manage a document set's lifecycle. Note, in
    * particular, that DocumentSetStore selects the job FOR UPDATE.
    */
  private[stores] def deletePending(job: DocumentSetCreationJob) : Unit = {
    require(job.state == DocumentSetCreationJobState.NotStarted || job.state == DocumentSetCreationJobState.Error)

    import org.overviewproject.postgres.SquerylEntrypoint._

    from(Schema.documentSetCreationJobs)(dscj =>
      where(dscj.id === job.id)
      select(&(lo_unlink(dscj.contentsOid)))
    ).toIterable // toIterable() executes it

    Schema.documentSetCreationJobs.delete(job.id)
  }

  /** Tells the worker to delete a DocumentSetCreationJob and some parts of its
    * associated DocumentSet.
    *
    * This only works for an InProgress or Cancelled job. If the job is not
    * running, use deletePending() instead.
    *
    * Because of this complex logic, this method is not public. Use
    * DocumentSetStore to manage a document set's lifecycle. Note, in
    * particular, that DocumentSetStore selects the job FOR UPDATE.
    */
  private[stores] def cancel(job: DocumentSetCreationJob) : Unit = {
    require(job.state == DocumentSetCreationJobState.InProgress || job.state == DocumentSetCreationJobState.Cancelled)

    insertOrUpdate(job.copy(state = DocumentSetCreationJobState.Cancelled))
  }

  override def delete(query: Query[DocumentSetCreationJob]) : Int = {
    throw new AssertionError("Do not delete() a DocumentSetCreationJob; cancel() or deletePending() it.")
  }

  override def delete[K](k: K)(implicit ked: KeyedEntityDef[DocumentSetCreationJob,K]) : Unit = {
    throw new AssertionError("Do not delete() a DocumentSetCreationJob; cancel() or deletePending() it.")
  }
}
