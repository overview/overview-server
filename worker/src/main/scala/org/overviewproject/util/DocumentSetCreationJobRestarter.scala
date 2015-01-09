package org.overviewproject.util

import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState._

trait DocumentSetCreationJobRestarter extends NewJobRestarter {


  override protected def removeInterruptedJobData: Unit =
    storage.deleteDocuments(job.documentSetId)

  protected val storage: DocumentStorage

  protected trait DocumentStorage extends Storage {
    def deleteDocuments(jobId: Long): Unit
  }

}