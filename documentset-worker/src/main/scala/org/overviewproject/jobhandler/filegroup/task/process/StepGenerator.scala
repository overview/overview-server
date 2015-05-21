package org.overviewproject.jobhandler.filegroup.task.process

import org.overviewproject.jobhandler.filegroup.task.step.FinalStep
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep

/**
 * Provides a framework for chaining [[TaskSteps]].
 * Implement the trait for a [[TaskStep]] with the specified Input and Output types.
 */
trait StepGenerator[Input, Output] {

  private var nextStepGenerator: Option[StepGenerator[Output, _]] = None
  
  private def finalStep(arg: Output): TaskStep = FinalStep

  
  /**
   * Set the [[StepGenerator]] for the next step in the chain
   * @param nextStep a [[StepGenerator]] with an input type equivalent to the output type
   * of this [[StepGenerator]]
   */
  def andThen(nextStep: StepGenerator[Output, _]): StepGenerator[Input, Output] = {
    nextStepGenerator = Some(nextStep)
    this
  }
  
  protected def nextStepFn: Output => TaskStep = 
    nextStepGenerator.map(_.generate _).getOrElse(finalStep)
    
  
    /**
     * Construct the [[TaskStep]]. Use nextStepFn to create the result of [[TaskStep.execute]].
     */
  def generate(param: Input): TaskStep
}