package org.overviewproject.util

import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState._

trait ClusteringJobRestarter {
  val MaxRetryAttempts = Configuration.getInt("max_job_retry_attempts")

  def restart(job: DocumentSetCreationJob): Unit = {
    if (storage.treeExists(job.id)) finishJob(job)
    else if (job.retryAttempts < MaxRetryAttempts) attemptRestart(job)
    else failJob(job)
  }

  private def attemptRestart(job: DocumentSetCreationJob): Unit = {
    storage.deleteNodes(job.id)
    storage.updateValidJob(job.copy(state = NotStarted, retryAttempts = job.retryAttempts + 1))
  }

  private def failJob(job: DocumentSetCreationJob): Unit =
    storage.updateValidJob(job.copy(state = Error, statusDescription = "max_retry_attempts"))

  private def finishJob(job: DocumentSetCreationJob): Unit = {
    storage.deleteDocumentSetCreationJobNode(job.id)
    storage.deleteJob(job.id)
  }

  protected val storage: Storage

  protected trait Storage {
    def updateValidJob(job: DocumentSetCreationJob): Unit
    def treeExists(jobId: Long): Boolean
    def deleteNodes(rootNodeId: Long): Unit
    def deleteDocumentSetCreationJobNode(jobId: Long): Unit
    def deleteJob(jobId: Long): Unit
  }
}