package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait TaskStep {

  def execute: Future[TaskStep] = {
    val r = doExecute
    
    r.onFailure {
      case t => errorHandler(t)
    }
    
    r
  } 


  protected def doExecute: Future[TaskStep]
  protected def errorHandler(t: Throwable): Unit = {}

}