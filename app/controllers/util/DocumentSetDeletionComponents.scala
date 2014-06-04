package controllers.util

import org.overviewproject.tree.orm.DocumentSetCreationJob
import models.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.tree.orm.DocumentSet
import models.orm.stores.DocumentSetStore
import org.overviewproject.jobs.models.{ CancelFileUpload, Delete, DeleteTreeJob }

trait DocumentSetDeletionComponents {

  trait DocumentSetDeletionStorage {
    def cancelJob(documentSetId: Long): Option[DocumentSetCreationJob] =
      DocumentSetCreationJobStore.findCancellableJobByDocumentSetAndCancel(documentSetId)

    def deleteDocumentSet(documentSet: DocumentSet): Unit =
      DocumentSetStore.markDeleted(documentSet)
  }

  trait DocumentSetDeletionJobMessageQueue {
    def send(deleteCommand: Delete): Unit = JobQueueSender.send(deleteCommand)
    def send(cancelFileUploadCommand: CancelFileUpload): Unit = JobQueueSender.send(cancelFileUploadCommand)
  }
}
