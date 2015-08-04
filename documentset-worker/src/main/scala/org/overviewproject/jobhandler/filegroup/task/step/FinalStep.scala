package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.Future

case object FinalStep extends TaskStep {
  
  override def execute: Future[TaskStep] = Future.successful(this)
}