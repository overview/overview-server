package org.overviewproject.util

import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState._

trait DocumentSetCreationJobRestarter {
  val MaxRetryAttempts = Configuration.getInt("max_job_retry_attempts")

  protected val job: DocumentSetCreationJob

  def restart: Unit = {
    if (job.retryAttempts < MaxRetryAttempts) {
      storage.deleteDocuments(job.documentSetId)
      storage.updateValidJob(job.copy(state = NotStarted, retryAttempts = job.retryAttempts + 1))
    } else storage.updateValidJob(job.copy(state = Error, statusDescription = "max_retry_attempts"))
  }

  protected val storage: Storage

  protected trait Storage {
    def deleteDocuments(jobId: Long): Unit
    def updateValidJob(job: DocumentSetCreationJob): Unit
  }
}