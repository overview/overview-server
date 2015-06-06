package org.overviewproject.util

import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState._
import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.searchindex.TransportIndexClient

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

object JobRestarter extends SlickSessionProvider {
  import scala.concurrent.Await
  import scala.concurrent.duration.Duration
  import scala.concurrent.ExecutionContext.Implicits.global
  import org.overviewproject.database.Slick.simple._
  import org.overviewproject.models.tables.DocumentSetCreationJobsImpl
  import org.overviewproject.models.DocumentSetCreationJobType._
  import org.overviewproject.models.DocumentSetCreationJobState._
  import org.overviewproject.models.tables.DocumentSetCreationJobs

  def restartInterruptedJobs: Unit = {
    interruptedJobs.flatMap(createRestarter).map(_.restart)
  }

  // FIXME: make async
  private def interruptedJobs: Seq[DocumentSetCreationJob] = {
    val jobList = db { implicit session =>
      DocumentSetCreationJobs.filter(_.state === InProgress).list
    }
    Await.result(jobList, Duration.Inf)
  }

  private def createRestarter(job: DocumentSetCreationJob): Option[JobRestarter] = job.jobType match {
    case Recluster     => Some(ClusteringJobRestarter(job))
    case DocumentCloud => Some(DocumentSetCreationJobRestarter(job, TransportIndexClient.singleton))
    case CsvUpload     => Some(DocumentSetCreationJobRestarter(job, TransportIndexClient.singleton))
    case _             => None
  }

}
