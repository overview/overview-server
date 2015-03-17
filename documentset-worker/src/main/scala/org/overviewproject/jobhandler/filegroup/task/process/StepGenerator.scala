package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.TaskStep

trait StepGenerator[Input, Output] {

  protected var nextStepGenerator: Option[StepGenerator[Output, _]] = None

  def andThen(nextStep: StepGenerator[Output, _]): StepGenerator[Input, Output] = {
    nextStepGenerator = Some(nextStep)
    this
  }
  
  def generate(param: Input): TaskStep
}