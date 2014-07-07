package org.overviewproject.jobhandler.filegroup.task

import akka.actor.Props
import org.overviewproject.test.ParameterStore

class TestFileGroupTaskWorker(override protected val jobQueuePath: String) extends FileGroupTaskWorker {

  val executeFn = ParameterStore[Unit]
  val deleteFileUploadJobFn = ParameterStore[(Long, Long)]

  private case class StepInSequence(n: Int, finalStep: FileGroupTaskStep) extends FileGroupTaskStep {
    def execute: FileGroupTaskStep = {
      executeFn.store()
      if (n > 0) StepInSequence(n - 1, finalStep)
      else finalStep
    }
  }

  override protected def startCreatePagesTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep =
    StepInSequence(1, CreatePagesProcessComplete(documentSetId, uploadedFileId, None))

  override protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Unit =
    deleteFileUploadJobFn.store((documentSetId, fileGroupId))

}

object TestFileGroupTaskWorker {
  def apply(jobQueuePath: String): Props = Props(new TestFileGroupTaskWorker(jobQueuePath))
}
