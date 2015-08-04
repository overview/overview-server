package controllers.util

import com.overviewdocs.jobs.models.{CancelFileUpload,Delete}

trait DocumentSetDeletionComponents {
  trait DocumentSetDeletionJobMessageQueue {
    def send(deleteCommand: Delete): Unit = JobQueueSender.send(deleteCommand)
    def send(cancelFileUploadCommand: CancelFileUpload): Unit = JobQueueSender.send(cancelFileUploadCommand)
  }
}
