package org.overviewproject.util

import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState._
import scala.concurrent.ExecutionContext

trait DocumentSetCreationJobRestarter extends NewJobRestarter {

  override protected def removeInterruptedJobData: Unit = {
    storage.deleteDocuments(job.documentSetId)
    searchIndex.deleteDocumentSetAliasAndDocuments(job.documentSetId)
  }

  protected val storage: DocumentStorage
  protected val searchIndex: SearchIndex
  
  protected trait DocumentStorage extends Storage {
    def deleteDocuments(jobId: Long): Unit
  }

}

object DocumentSetCreationJobRestarter {

  def apply(job: DocumentSetCreationJob, searchIndex: SearchIndex)(implicit executionContext: ExecutionContext): DocumentSetCreationJobRestarter = ???

  private class DocumentSetCreationJobRestarterWithStorage(val job: DocumentSetCreationJob, val searchIndex: SearchIndex)(
    implicit executionContext: ExecutionContext) extends DocumentSetCreationJobRestarter {

    import scala.concurrent.{ Await, Future }
    import scala.concurrent.duration.Duration
    import org.overviewproject.database.SlickSessionProvider

    override protected val storage = new DbSyncedStorage
    
    class DbSyncedStorage extends DocumentStorage {
      private def await[A](block: => Future[A]): A =
        Await.result(block, Duration.Inf)

      private val cleaner = new DocumentSetCleaner with SlickSessionProvider {
        override implicit protected val executor = executionContext
      }

      override def updateValidJob(job: DocumentSetCreationJob): Unit =
        await(cleaner.updateValidJob(job))

      override def deleteDocuments(documentSetId: Long): Unit =
        await(cleaner.deleteDocuments(documentSetId))
    }
  }
}