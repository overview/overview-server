package org.overviewproject.runner

import java.io.File
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{Process, ProcessLogger}

import org.overviewproject.runner.commands.Command

class Daemon(val logger: ProcessLogger, val command: Command) {
  logger.out("Running " + command)

  val process = command.processBuilder.run(logger)

  val statusCodeFuture : Future[Int] = Future({ process.exitValue() })(ExecutionContext.global)
}
