package org.overviewproject.jobhandler.filegroup.task

import java.io.ByteArrayOutputStream
import scala.sys.process._
import scala.language.postfixOps
import scala.util.control.Exception._
import scala.concurrent.Future
import scala.concurrent.blocking
import java.io.InputStream

/**
 * Run an external command, returning any output
 * @param command the command to run. Must be a single command.
 */
class CommandRunner(command: String, timeoutGenerator: TimeoutGenerator) {

  /**
   * Starts running the command.
   * @returns [[RunningCommand]] that can be cancelled or provide the command exit value
   */
  val runAsync: RunningCommand = {
    val outputCatcher = new OutputCatcher
    val commandProcess = Process(command)

    val process = commandProcess.run(outputCatcher.log)

    new RunningShellCommand(process, outputCatcher)
  }

  
  private class OutputCatcher {
    private var outputLog: String = ""

    private def saveOutput(line: String): Unit = outputLog += s"$line\n"

    def log = ProcessLogger(saveOutput, saveOutput)

    def output = outputLog
  }

  private class RunningShellCommand(process: Process, outputCatcher: OutputCatcher) extends RunningCommand {
    import scala.concurrent.ExecutionContext.Implicits.global

    override def cancel: Unit = process.destroy

    override def result: Future[String] = waitForExitValue.collect {
      case v if success(v) => outputCatcher.output
      case _               => throw new CommandFailedException(outputCatcher.output)
    }

    private def success(exitCode: Int) = exitCode == 0

    private def waitForExitValue: Future[Int] = Future {
      blocking { process.exitValue }
    }

  }
}

class CommandFailedException(output: String) extends Exception(output)

/** A command currently being executed */
trait RunningCommand {
  /** Interrupt the running command */
  def cancel: Unit

  /**
   * @returns the output of the command in the [[Future]]. If the command returned a non-zero exit code, the output
   * is a [[Left]], otherwise a [[Right]].
   */
  def result: Future[String]
}

