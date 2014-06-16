package org.overviewproject.jobhandler.filegroup.task

/**
 * Interface for steps in a process that can be cancelled.
 */
trait FileGroupTaskStep {
  /** 
   *  Execute one step and return the next step. It is the callers responsibility to
   *  decide whether the next step should be executed, or if the process is complete or
   *  cancelled.
   */
  def execute: FileGroupTaskStep
  
  /** Add implementation if cleanup is needed when the process is cancelled */
  def cancel: Unit = {}
}


