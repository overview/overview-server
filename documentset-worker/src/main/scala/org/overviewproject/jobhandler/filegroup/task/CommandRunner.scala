package org.overviewproject.jobhandler.filegroup.task

import java.io.ByteArrayOutputStream
import scala.sys.process._
import scala.language.postfixOps
import scala.util.control.Exception._
import scala.concurrent.Future

/**
 * Run an external command, returning any output
 * @param command the command to run. Must be a single command.
 */
class CommandRunner(command: String) {

  /**
   * Starts running the command.
   * @returns [[RunningCommand]] that can be cancelled or provide the command exit value
   */
  val runAsync: RunningCommand = {
    val outputCatcher = new OutputCatcher
    val commandProcess = Process(command)

    // catch Exception thrown if command is not found
    val process = allCatch either {
      commandProcess.run(outputCatcher.log)
    }

    new RunningShellCommand(process, outputCatcher)
  }
  
  private def success(exitCode: Int) = exitCode == 0

  private class OutputCatcher {
    private var outputLog: String = ""

    private def saveOutput(line: String): Unit = outputLog += s"$line\n"

    def log = ProcessLogger(saveOutput, saveOutput)

    def output = outputLog
  }

  private class RunningShellCommand(process: Either[Throwable, Process], outputCatcher: OutputCatcher) extends RunningCommand {
    import scala.concurrent.ExecutionContext.Implicits.global
    
    override def cancel: Unit = process.right.map(_.destroy)
    
    override def result: Future[Either[String, String]] = Future {
      val result = process.right.map(_.exitValue)

      result match {
        case r @ Right(exitCode) if success(exitCode) => Right(outputCatcher.output)
        case r @ Left(e) => Left(s"${e.getMessage()}\nOutput: ${outputCatcher.output}\n")
        case _ => Left(s"Output: ${outputCatcher.output}")
      }

    }

  }
}

/** A command currently being executed */
trait RunningCommand {
  /** Interrupt the running command */
  def cancel: Unit
  
  /** 
   * @returns the output of the command in the [[Future]]. If the command returned a non-zero exit code, the output
   * is a [[Left]], otherwise a [[Right]]. 
   */
  def result: Future[Either[String, String]]
}

