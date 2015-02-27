package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future

case class WaitForResponse(nextStep: TaskStep) extends TaskStep {
  override def execute: Future[TaskStep] = Future.successful(nextStep)
}
