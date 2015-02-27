package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future

case class WaitForResponse(nextStepForResponse: Seq[Long] => TaskStep) extends TaskStep {
  override def execute: Future[TaskStep] = Future.successful(this)
}
