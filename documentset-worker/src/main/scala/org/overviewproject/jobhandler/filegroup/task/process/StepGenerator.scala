package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.FinalStep
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep

trait StepGenerator[Input, Output] {

  private var nextStepGenerator: Option[StepGenerator[Output, _]] = None
  
  private def finalStep(arg: Output): TaskStep = FinalStep

  def andThen(nextStep: StepGenerator[Output, _]): StepGenerator[Input, Output] = {
    nextStepGenerator = Some(nextStep)
    this
  }
  
  protected def nextStepFn: Output => TaskStep = 
    nextStepGenerator.map(_.generate _).getOrElse(finalStep)
    
  
  def generate(param: Input): TaskStep
}