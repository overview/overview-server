package org.overviewproject.jobhandler.filegroup.task

import akka.actor.Props
import org.overviewproject.test.ParameterStore
import akka.actor.ActorRef

class TestFileGroupTaskWorker(override protected val jobQueuePath: String,
                              override protected val progressReporterPath: String, outputFileId: Long) extends FileGroupTaskWorker {

  val executeFn = ParameterStore[Unit]
  val deleteFileUploadJobFn = ParameterStore[(Long, Long)]

  private case class StepInSequence(n: Int, finalStep: FileGroupTaskStep) extends FileGroupTaskStep {
    def execute: FileGroupTaskStep = {
      executeFn.store()
      if (n > 0) StepInSequence(n - 1, finalStep)
      else finalStep
    }
  }

  override protected def startCreatePagesTask(documentSetId: Long, uploadedFileId: Long): FileGroupTaskStep =
    StepInSequence(1, CreatePagesProcessComplete(documentSetId, uploadedFileId, Some(outputFileId)))

  override protected def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean,
                                                  progressReporter: ActorRef): FileGroupTaskStep =
    StepInSequence(1, CreateDocumentsProcessComplete(documentSetId))

  override protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Unit =
    deleteFileUploadJobFn.store((documentSetId, fileGroupId))

}

object TestFileGroupTaskWorker {
  def apply(jobQueuePath: String, progressReporterPath: String, outputFileId: Long): Props =
    Props(new TestFileGroupTaskWorker(jobQueuePath, progressReporterPath, outputFileId))
}
