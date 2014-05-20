package org.overviewproject.jobhandler.filegroup

import scala.concurrent.ExecutionContext
import akka.agent.Agent
import scala.concurrent.Future
import akka.actor.Props
import scala.collection.immutable.Queue

class TestFileGroupTaskWorker(override protected val jobQueuePath: String) extends FileGroupTaskWorker {
  import ExecutionContext.Implicits.global

  private val timesStartCreatePagesTaskWasCalled: Agent[Int] = Agent(0)
  private val deleteFileUploadJobCalls: Agent[Queue[(Long, Long)]] = Agent(Queue.empty)

  private case class StepInSequence(n: Int, finalStep: FileGroupTaskStep) extends FileGroupTaskStep {
    def execute: FileGroupTaskStep = {
      timesStartCreatePagesTaskWasCalled send (_ + 1)
      if (n > 0) StepInSequence(n - 1, finalStep)
      else finalStep
    }
  }

  override protected def startCreatePagesTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep =
    StepInSequence(1, CreatePagesProcessComplete(documentSetId, fileGroupId, uploadedFileId))

  override protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Unit =
    deleteFileUploadJobCalls send (_.enqueue((documentSetId, fileGroupId)))

  def startCreatePagesTaskCallsInProgress: Future[Int] = timesStartCreatePagesTaskWasCalled.future
  def numberOfStartCreatePagesTaskCalls: Int = timesStartCreatePagesTaskWasCalled.get
  
  def deleteFileUploadJobCallsInProgress: Future[Queue[(Long, Long)]] = deleteFileUploadJobCalls.future
  def deleteFileUploadJobCallParameters: Option[(Long, Long)] = deleteFileUploadJobCalls.get.headOption
}

object TestFileGroupTaskWorker {
  def apply(jobQueuePath: String): Props = Props(new TestFileGroupTaskWorker(jobQueuePath))
}
