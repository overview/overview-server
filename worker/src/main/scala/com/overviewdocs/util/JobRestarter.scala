package com.overviewdocs.util

import com.overviewdocs.models.DocumentSetCreationJob
import com.overviewdocs.models.DocumentSetCreationJobState._
import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.searchindex.TransportIndexClient

trait JobRestarter {
  val MaxRetryAttempts = Configuration.getInt("max_job_retry_attempts")

  protected val job: DocumentSetCreationJob
  protected val storage: Storage

  def restart: Unit =
    if (job.retryAttempts < MaxRetryAttempts) attemptRestart
    else failJob

  protected def attemptRestart: Unit = {
    removeInterruptedJobData
    storage.updateValidJob(job.copy(state = NotStarted, retryAttempts = job.retryAttempts + 1))
  }

  protected def failJob: Unit =
    storage.updateValidJob(job.copy(state = Error, statusDescription = "max_retry_attempts"))

  protected def removeInterruptedJobData: Unit

  protected trait Storage {
    def updateValidJob(job: DocumentSetCreationJob)
  }
}

object JobRestarter extends HasBlockingDatabase {
  import scala.concurrent.ExecutionContext.Implicits.global
  import com.overviewdocs.models.DocumentSetCreationJobType._
  import com.overviewdocs.models.tables.DocumentSetCreationJobs

  import database.api._

  def restartInterruptedJobs: Unit = {
    interruptedJobs.flatMap(createRestarter).map(_.restart)
  }

  // FIXME: make async
  private def interruptedJobs: Seq[DocumentSetCreationJob] = {
    blockingDatabase.seq(DocumentSetCreationJobs.filter(_.state === InProgress))
  }

  private def createRestarter(job: DocumentSetCreationJob): Option[JobRestarter] = job.jobType match {
    case DocumentCloud => Some(DocumentSetCreationJobRestarter(job, TransportIndexClient.singleton))
    case CsvUpload     => Some(DocumentSetCreationJobRestarter(job, TransportIndexClient.singleton))
    case _             => None
  }

}
