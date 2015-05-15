package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future

trait TaskStep {
  
  def execute: Future[TaskStep] = doExecute
  
  protected def doExecute: Future[TaskStep]

}