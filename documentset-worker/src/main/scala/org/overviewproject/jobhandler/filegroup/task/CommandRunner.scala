package org.overviewproject.jobhandler.filegroup.task

import java.io.ByteArrayOutputStream
import scala.sys.process._
import scala.language.postfixOps
import scala.util.control.Exception._

/**
 * Run an external command, returning any output 
 * @param command the command to run. Must be a single command.
 */
class CommandRunner(command: String) {
  
  /** 
   *  Runs the command, waiting for it to complete 
   *  @return The output in a `Right[String]` on success, and in a `Left[String]` otherwise. 
   */
  val run: Either[String, String] = {
    val outputCatcher = new OutputCatcher
    val commandProcess = Process(command)  

    // catch Exception thrown if command is not found
    val result = allCatch either {
       commandProcess ! outputCatcher.log
    }
    
    // Return output, converting exception to failure 
    result match {
      case r @ Right(exitCode) if success(exitCode) => Right(outputCatcher.output)
      case r @ Left(e) => Left(e.getMessage())
      case _ => Left(outputCatcher.output)
    }
    
  }
  
  private def success(exitCode: Int) = exitCode == 0
  
  private class OutputCatcher {
    private var outputLog: String = ""
    
    private def saveOutput(line: String): Unit = outputLog += s"$line\n"
      
    def log = ProcessLogger(saveOutput, saveOutput) 
    
    def output = outputLog
  }
}