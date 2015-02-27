package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Future

class WaitForResponseSpec extends Specification {

  "WaitForResponse" should {

    "provide noop execute" in new WaitScope {
      waitForResponse.execute must be_==(waitForResponse).await
    }
    
    "return the next step" in new WaitScope {
      waitForResponse.nextStepForResponse(Seq(1l)) must be equalTo TestTaskStep
    }
  }

  trait WaitScope extends Scope {
    val waitForResponse = WaitForResponse(nextStep)
    def nextStep(ids: Seq[Long]) = TestTaskStep
  }

  object TestTaskStep extends TaskStep {
    override def execute = Future.successful(this)
  }
}

