package com.overviewdocs.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext


class FinalStepSpec extends Specification {
  
  "FinalStep" should {
    
    "return itself as the next step" in {
      FinalStep.execute must be_==(FinalStep).await
    }
  }
}