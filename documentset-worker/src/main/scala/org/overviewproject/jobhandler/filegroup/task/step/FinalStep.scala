package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future

case object FinalStep extends TaskStep {
  override protected def doExecute: Future[TaskStep] = Future.successful(this)
}