package org.overviewproject.util

import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState._
import scala.concurrent.ExecutionContext

trait ClusteringJobRestarter {
  val MaxRetryAttempts = Configuration.getInt("max_job_retry_attempts")

  protected val job: DocumentSetCreationJob

  def restart: Unit = {
    if (storage.treeExists(job.id)) finishJob
    else if (job.retryAttempts < MaxRetryAttempts) attemptRestart
    else failJob
  }

  private def attemptRestart: Unit = {
    storage.deleteNodes(job.id)
    storage.updateValidJob(job.copy(state = NotStarted, retryAttempts = job.retryAttempts + 1))
  }

  private def failJob: Unit =
    storage.updateValidJob(job.copy(state = Error, statusDescription = "max_retry_attempts"))

  private def finishJob: Unit = {
    storage.deleteDocumentSetCreationJobNode(job.id)
    storage.deleteJob(job.id)
  }

  protected val storage: Storage

  protected trait Storage {
    def updateValidJob(job: DocumentSetCreationJob): Unit
    def treeExists(jobId: Long): Boolean
    def deleteNodes(jobId: Long): Unit
    def deleteDocumentSetCreationJobNode(jobId: Long): Unit
    def deleteJob(jobId: Long): Unit
  }
}

object ClusteringJobRestarter {
  def apply(job: DocumentSetCreationJob)(implicit executionContext: ExecutionContext): ClusteringJobRestarter = new ClusteringJobRestarterWithStorage(job)

  private class ClusteringJobRestarterWithStorage(val job: DocumentSetCreationJob)
    (implicit executionContext: ExecutionContext) extends ClusteringJobRestarter {
    import scala.concurrent.{ Await, Future }
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.duration.Duration
    import scala.slick.jdbc.StaticQuery.interpolation
    import org.overviewproject.database.SlickSessionProvider
    import org.overviewproject.database.Slick.simple._
    import org.overviewproject.models.DocumentSetCreationJobState
    import org.overviewproject.models.tables.{ DocumentSetCreationJobs, Trees }

    override protected val storage = new DbSyncedStorage

    // Wait for db access Futures to complete
    // until we can make caller deal with Futures
    class DbSyncedStorage extends Storage {
      
      private val cleaner = new ClusteringCleaner with SlickSessionProvider {
        override implicit protected val executor = executionContext
      }
      
      override def updateValidJob(job: DocumentSetCreationJob): Unit = 
        cleaner.updateValidJob(job)

      override def treeExists(jobId: Long): Boolean = ??? 
      override def deleteNodes(rootNodeId: Long): Unit = ???
      override def deleteDocumentSetCreationJobNode(jobId: Long): Unit = ???
      override def deleteJob(jobId: Long): Unit = ???

    }
  }

}