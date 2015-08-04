package com.overviewdocs.util

import scala.concurrent.ExecutionContext

import com.overviewdocs.models.DocumentSetCreationJob
import com.overviewdocs.models.DocumentSetCreationJobState._
import com.overviewdocs.searchindex.IndexClient

trait DocumentSetCreationJobRestarter extends JobRestarter {

  override protected def removeInterruptedJobData: Unit = {
    storage.deleteDocuments(job.documentSetId)
    searchIndex.removeDocumentSet(job.documentSetId)
  }

  protected val storage: DocumentStorage
  protected val searchIndex: IndexClient

  protected trait DocumentStorage extends Storage {
    def deleteDocuments(jobId: Long): Unit
  }
}

object DocumentSetCreationJobRestarter {

  def apply(job: DocumentSetCreationJob, searchIndex: IndexClient)(
      implicit executionContext: ExecutionContext): DocumentSetCreationJobRestarter =
    new DocumentSetCreationJobRestarterWithStorage(job, searchIndex)

  private class DocumentSetCreationJobRestarterWithStorage(val job: DocumentSetCreationJob, val searchIndex: IndexClient)(implicit executionContext: ExecutionContext) extends DocumentSetCreationJobRestarter {

    import scala.concurrent.{ Await, Future }
    import scala.concurrent.duration.Duration

    override protected val storage = new DbSyncedStorage

    class DbSyncedStorage extends DocumentStorage {
      private def await[A](block: => Future[A]): A =
        Await.result(block, Duration.Inf)

      private val cleaner = DocumentSetCleaner

      override def updateValidJob(job: DocumentSetCreationJob): Unit =
        await(cleaner.updateValidJob(job))

      override def deleteDocuments(documentSetId: Long): Unit =
        await(cleaner.deleteDocuments(documentSetId))
    }
  }
}
