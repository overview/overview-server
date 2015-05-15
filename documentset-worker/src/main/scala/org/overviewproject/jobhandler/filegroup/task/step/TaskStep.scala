package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future
import scala.util.control.Exception._

trait TaskStep {

  def execute: Future[TaskStep] = nonFatalCatch.withApply(handleAndRethrow) {
    doExecute
  }

  protected def doExecute: Future[TaskStep]
  protected def errorHandler(t: Throwable): Unit = {}

  private def handleAndRethrow(t: Throwable) = {
    errorHandler(t)
    throw t
  }
}