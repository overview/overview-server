package com.overviewdocs.jobhandler.filegroup.task


/**
 * In order to complete a task, a number of [[TaskStep]]s need to be executed by 
 * the [[FileGroupTaskWorker]]. The output of [[TaskStep.execute]] is the next step
 * (until [[FinalStep]] is returned.
 * Each [[TaskStep]] needs some input, and generates some output, which is then passed on
 * to the next step. Concrete subclasses of [[TaskStep]] typically contain a `nextStepFn` 
 * which generate the next step based on the generated output. However, a [[TaskStep]] may 
 * require additional information during construction. Rather than forcing [[TaskStep]]s p
 * pass through unrelated information that will be needed later in the chain of steps,
 * [[StepGenerator]]s are used to store the needed information until [[TaskStep]] 
 * creation time. [[StepGenerator]] are chained together, providing the `nextStepFn` needed
 * by [[TaskStep]]s.  
 * 
 */
package object process {

}