package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._

import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.Ownership
import models.orm.Schema

object DocumentSetCreationJobFinder {
  import scala.language.postfixOps

  /** @return All DocumentSetCreationJobs with the given ID.
    *
    * Since ID is a unique key, the return value can only have 0 or 1 row.
    */
  def byDocumentSet(documentSet: Long) = {
    from(Schema.documentSetCreationJobs)(dscj =>
      where(dscj.documentSetId === documentSet)
      select(dscj)
    )
  }

  /** @return All DocumentSetCreationJobs.
    *
    * These are ordered from newest to oldest.
    */
  def all = {
    from(Schema.documentSetCreationJobs)(dscj =>
      select(dscj)
      orderBy(dscj.id desc)
    )
  }

  /** @return All DocumentSetCreationJobs owned by the given user.
    *
    * These are ordered from newest to oldest.
    */
  def byUserWithDocumentSetsAndUploadedFiles(user: String) = {
    from(documentSetCreationJobsWithDocumentSetsAndUploadedFiles)(tuple =>
      where(tuple._1.documentSetId in DocumentSetFinder.documentSetIdsForUser(user, Ownership.Owner))
      select(tuple)
      orderBy(tuple._1.id desc)
    )
  }

  /** @return All (DocumentSetCreationJob,DocumentSet,Option[UploadedFile]) tuples.
   *
   * The return value must be filtered further.
   */
  private def documentSetCreationJobsWithDocumentSetsAndUploadedFiles = {
    join(Schema.documentSetCreationJobs, Schema.documentSets, Schema.uploadedFiles.leftOuter)((dscj, ds, uf) =>
      select(dscj, ds, uf)
      on(dscj.documentSetId === ds.id, ds.uploadedFileId === uf.map(_.id))
    )
  }
}
