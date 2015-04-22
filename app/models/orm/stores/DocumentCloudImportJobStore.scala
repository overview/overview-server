package models.orm.stores

import org.overviewproject.tree.orm.{ DocumentSetCreationJob, DocumentSetCreationJobState }
import org.overviewproject.tree.DocumentSetCreationJobType
import models.DocumentCloudImportJob

object DocumentCloudImportJobStore {
  /** Creates a new DocumentCloudImportJob in the database. */
  def insert(documentSetId: Long, job: DocumentCloudImportJob) : DocumentSetCreationJob = {
    DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
      documentSetId = documentSetId,
      state = DocumentSetCreationJobState.NotStarted,
      jobType = DocumentSetCreationJobType.DocumentCloud,
      lang = job.lang,
      documentcloudUsername = job.credentials.map(_.username),
      documentcloudPassword = job.credentials.map(_.password),
      splitDocuments = job.splitDocuments,
      suppliedStopWords = job.suppliedStopWords,
      importantWords = job.importantWords
    ))
  }
}
