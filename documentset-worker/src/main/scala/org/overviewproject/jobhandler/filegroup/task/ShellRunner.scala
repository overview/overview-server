package com.overviewdocs.jobhandler.filegroup.task

import scala.Left
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.Exception.handling
import scala.concurrent.TimeoutException
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import java.io.InputStream

trait ShellRunner {
  protected val timeoutGenerator: TimeoutGenerator

  def run(command: String, timeout: FiniteDuration): Future[String] = {
    val commandRunner = new CommandRunner(command, timeoutGenerator)
    val runningCommand = commandRunner.runAsync
    
    runCommandWithTimeout(runningCommand)
  }

  
  private def runCommandWithTimeout(runningCommand: RunningCommand): Future[String] = {
    def cancellingProcessAfterTimeout(process: RunningCommand) =
      handling(classOf[TimeoutException]) by { _ =>
        process.cancel
        Future.failed(new Exception(timeoutErrorMessage))
      }

    cancellingProcessAfterTimeout(runningCommand) {
      runningCommand.result
    }

  }

  private def timeoutErrorMessage = s"Timeout Exceeded: command"

}

object ShellRunner {
  def apply(timeoutGenerator: TimeoutGenerator): ShellRunner =
    new ShellRunnerImpl(timeoutGenerator)

  private class ShellRunnerImpl(override protected val timeoutGenerator: TimeoutGenerator) extends ShellRunner
}
