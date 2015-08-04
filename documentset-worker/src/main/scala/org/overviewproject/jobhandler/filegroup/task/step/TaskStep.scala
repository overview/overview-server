package com.overviewdocs.jobhandler.filegroup.task.step


import scala.concurrent.Future

trait TaskStep {
  
  def execute: Future[TaskStep] 


}