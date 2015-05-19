package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Future
import scala.util.Failure
import scala.util.control.NonFatal

class TaskStepSpec extends Specification {

  "TaskStep" should {

    "call doExecute" in new TaskContext {
      val r = step.execute

      r must beEqualTo(FinalStep).await
    }

    "call error handler on exception" in new FailingTaskContext {
      val r = step.execute
      
      r.isCompleted must beTrue
      r.failed must beEqualTo(error).await
      
      errorHandlerWasCalled must beTrue
    }
  }

  trait TaskContext extends Scope {
    val step = createTaskStep

    def createTaskStep: TaskStep = new TestTaskStep

    class TestTaskStep extends TaskStep {
      override protected def doExecute = Future.successful(FinalStep)
    }
  }

  trait FailingTaskContext extends TaskContext {
    override def createTaskStep: TaskStep = new FailingTaskStep

    val error = new Exception("failure")
    var errorHandlerWasCalled = false

    class FailingTaskStep extends TaskStep {
      override protected def doExecute = Future.failed(error)

      override protected def errorHandler(e: Throwable) = errorHandlerWasCalled = true
    }
  }

}