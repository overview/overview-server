package org.overviewproject.jobhandler.filegroup

import scala.concurrent.ExecutionContext
import akka.agent.Agent
import scala.concurrent.Future
import akka.actor.Props

class TestFileGroupTaskWorker(override protected val jobQueuePath: String) extends FileGroupTaskWorker {
  import ExecutionContext.Implicits.global

  private val timesStartTaskWasCalled: Agent[Int] = Agent(0)

  private case class StepInSequence(n: Int, finalStep: FileGroupTaskStep) extends FileGroupTaskStep {
    def execute: FileGroupTaskStep = {
      timesStartTaskWasCalled send (_ + 1)
      if (n > 0) StepInSequence(n - 1, finalStep)
      else finalStep
    }
  }

  override protected def startCreatePagesTask(fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep =
    StepInSequence(1, CreatePagesTaskDone(fileGroupId, uploadedFileId))

  def startTaskCallsInProgress: Future[Int] = timesStartTaskWasCalled.future
  def numberOfStartTaskCalls: Int = timesStartTaskWasCalled.get
}

object TestFileGroupTaskWorker {
  def apply(jobQueuePath: String): Props = Props(new TestFileGroupTaskWorker(jobQueuePath))
}
