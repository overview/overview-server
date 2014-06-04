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
   * Finds a cancellable, non-Recluster job for the given document set, and sets its state to CANCELLED.
   *
   * There can be at most one import job (DocumentCloud, CsvUpload, Clone, FileUpload) for a given document set.
   *
   * Only the cancellable job is returned. If there is no cancellable job, None is returned
   *
   * The returned job has the original state (ie. not CANCELLED)
   *
   * The job is locked, so try to finish the transaction quickly.
   *
   * This method is actually a lot simpler than what we had before. [adam edit - even simpler now!]
   */
  def findCancellableJobByDocumentSetAndCancel(documentSetId: Long): Option[DocumentSetCreationJob] = {
    import org.overviewproject.postgres.SquerylEntrypoint._

    val runningJobs = from(Schema.documentSetCreationJobs)(dscj =>
      where(dscj.documentSetId === documentSetId and dscj.jobType <> Recluster)
      select(dscj)
    ).forUpdate

    val firstJob = runningJobs.headOption // There should be at most one running job

    firstJob.map { j => insertOrUpdate(j.copy(state = Cancelled)) }

    firstJob
  }

  override def delete(query: Query[DocumentSetCreationJob]): Int = {
    throw new AssertionError("Do not delete() a DocumentSetCreationJob; deletion is handled by workers.")
  }

  override def delete[K](k: K)(implicit ked: KeyedEntityDef[DocumentSetCreationJob, K]): Unit = {
    throw new AssertionError("Do not delete() a DocumentSetCreationJob; deletion is handled by workers.")
  }
}
