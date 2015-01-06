package org.overviewproject.util

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobType._
import org.overviewproject.models.DocumentSetCreationJobState._

class ClusteringJobRestarterSpec extends Specification with Mockito {

  "ClusteringJobRestarter" should {

    "remove job if tree exists" in new CompleteTree {
      jobRestarter.restart

      there was one(jobRestarter.mockStorage).deleteDocumentSetCreationJobNode(jobId)
      there was one(jobRestarter.mockStorage).deleteJob(jobId)
    }

    "delete nodes and restart job" in new JobScope {
      jobRestarter.restart

      there was one(jobRestarter.mockStorage).deleteNodes(jobId)
      there was one(jobRestarter.mockStorage).updateValidJob(job.copy(state = NotStarted, retryAttempts = retryAttempts + 1))
    }

    "fail job if restart limit is reached" in new MaxRetryAttemptsScope {
      jobRestarter.restart

      there was one(jobRestarter.mockStorage).updateValidJob(job.copy(state = Error,
        statusDescription = "max_retry_attempts"))
    }
  }

  trait JobScope extends Scope {
    val jobId = 1l
    val rootNodeId = 5l
    val job = DocumentSetCreationJob(jobId, 10l, Recluster, retryAttempts, "en", "", "", false,
      None, None, None, None, None, Some("tree"), None, None, InProgress, 0.4, "")

    val jobRestarter = new TestClusteringJobRestarter(job, isTreeComplete)

    protected def isTreeComplete = false
    protected def retryAttempts = 0
  }

  trait CompleteTree extends JobScope {
    override def isTreeComplete = true
  }

  trait MaxRetryAttemptsScope extends JobScope {
    override protected def retryAttempts = Configuration.getInt("max_job_retry_attempts")
  }

  class TestClusteringJobRestarter(val job: DocumentSetCreationJob, treeIsComplete: Boolean) extends ClusteringJobRestarter {
    override protected val storage = smartMock[Storage]

    storage.treeExists(any) returns treeIsComplete

    def mockStorage = storage
  }
}