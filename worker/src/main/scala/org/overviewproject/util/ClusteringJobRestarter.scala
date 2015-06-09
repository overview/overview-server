package org.overviewproject.util

import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState._
import scala.concurrent.ExecutionContext

/**
 * Restarts an interrupted clustering job, if necessary.
 * If the tree exists, the job is finished, and simply deleted. Otherwise,
 * previously created nodes are deleted.
 */
trait ClusteringJobRestarter extends JobRestarter {

  override def restart: Unit = {
    if (storage.treeExists(job.id)) finishJob
    else super.restart
  }

  override protected def removeInterruptedJobData = 
    storage.deleteNodes(job.id)

  private def finishJob: Unit = {
    storage.deleteJob(job.id)
  }

  override protected val storage: NodeStorage

  protected trait NodeStorage extends Storage {
    def treeExists(jobId: Long): Boolean
    def deleteNodes(jobId: Long): Unit
    def deleteJob(jobId: Long): Unit
  }
}

object ClusteringJobRestarter {
  def apply(job: DocumentSetCreationJob)(implicit executionContext: ExecutionContext): ClusteringJobRestarter = new ClusteringJobRestarterWithStorage(job)

  private class ClusteringJobRestarterWithStorage(val job: DocumentSetCreationJob)(implicit executionContext: ExecutionContext) extends ClusteringJobRestarter {
    import scala.concurrent.{ Await, Future }
    import scala.concurrent.duration.Duration
    import org.overviewproject.database.DatabaseProvider

    override protected val storage = new DbSyncedStorage

    // Wait for db access Futures to complete
    // until we can make caller deal with Futures
    class DbSyncedStorage extends NodeStorage {

      private def await[A](block: => Future[A]): A =
        Await.result(block, Duration.Inf)

      private val cleaner = new ClusteringCleaner with DatabaseProvider 

      override def updateValidJob(job: DocumentSetCreationJob): Unit =
        await(cleaner.updateValidJob(job))

      override def treeExists(jobId: Long): Boolean =
        await(cleaner.treeExists(jobId))

      override def deleteNodes(rootNodeId: Long): Unit =
        await(cleaner.deleteNodes(rootNodeId))

      override def deleteJob(jobId: Long): Unit =
        await(cleaner.deleteJob(jobId))

    }
  }

}
