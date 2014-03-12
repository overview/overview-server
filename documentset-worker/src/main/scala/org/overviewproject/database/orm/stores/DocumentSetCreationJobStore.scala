package org.overviewproject.database.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.database.orm.Schema.documentSetCreationJobs

object DocumentSetCreationJobStore extends BaseStore(documentSetCreationJobs) {

  def deleteNonRunningJobs(documentSetId: Long): Unit = {
    val deleteCsvUploadContent = from(documentSetCreationJobs)(dscj =>
      where(
        dscj.documentSetId === documentSetId and
          dscj.jobType === CsvUpload and
          dscj.state <> InProgress and
          dscj.state <> Cancelled)
        select (&(lo_unlink(dscj.contentsOid)))).toIterable

    val jobsToDelete = from(documentSetCreationJobs)(dscj =>
      where(
        dscj.documentSetId === documentSetId and
          dscj.state <> InProgress and
          dscj.state <> Cancelled)
        select (dscj))

    documentSetCreationJobs.delete(jobsToDelete)
  }

}