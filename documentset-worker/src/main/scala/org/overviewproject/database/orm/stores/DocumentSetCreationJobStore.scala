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
          dscj.state <> InProgress)
        select (&(lo_unlink(dscj.contentsOid)))).toIterable

    val jobsToDelete = from(documentSetCreationJobs)(dscj =>
      where(
        dscj.documentSetId === documentSetId and
          dscj.state <> InProgress)
        select (dscj))

    documentSetCreationJobs.delete(jobsToDelete)
  }

  // Should only be called on a reclustering job
  def deleteByState(documentSetId: Long, jobState: DocumentSetCreationJobState): Unit = {
    val jobsToDelete = from(documentSetCreationJobs)(dscj =>
      where(dscj.documentSetId === documentSetId and
        dscj.state === jobState)
        select (dscj))

    documentSetCreationJobs.delete(jobsToDelete)
  }

  def deleteById(id: Long): Unit = {
    val jobToDelete = from(documentSetCreationJobs)(dscj =>
      where(dscj.id === id)
        select (dscj))
        
    documentSetCreationJobs.delete(jobToDelete)
  }
}