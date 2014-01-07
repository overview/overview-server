package org.overviewproject.runner

import java.io.{ FilterOutputStream, OutputStream, PrintStream }
import scala.sys.process.ProcessLogger

class Logger(private val baseOut: OutputStream, private val baseErr: OutputStream, val prefixAnsi: Option[Array[Byte]] = None) {
  val outAnsiPrefix = prefixAnsi.getOrElse(Array[Byte]())
  val errAnsiPrefix = Array.concat("\033[31;1mERROR - \033[0m".getBytes(), prefixAnsi.getOrElse(Array[Byte]()))

  val out = Logger.wrapOutputStream(baseOut, outAnsiPrefix)
  val err = Logger.wrapOutputStream(baseErr, errAnsiPrefix)

  def sublogger(newPrefix : String, newPrefixAnsiCode : Option[Array[Byte]]): Logger = {
    val newPrefixAnsi = newPrefixAnsiCode match {
      case Some(code) => Array.concat(code, s"[${newPrefix}] ".getBytes(), Console.RESET.getBytes())
      case None => s"[${newPrefix}] ".getBytes()
    }

    new Logger(baseOut, baseErr, Some(newPrefixAnsi))
  }

  def toProcessLogger : ProcessLogger = ProcessLogger(out.println, err.println)
}

object Logger {
  private class AnsiOutputStream(outputStream: OutputStream, prefixAnsi: Array[Byte]) extends FilterOutputStream(outputStream) {
    final val NEWLINE = "\n".codePointAt(0)
    var startingLine = true

    override def write(b: Int) : Unit = {
      if (startingLine) {
        out.write(prefixAnsi)
      }

      out.write(b)

      if (b == NEWLINE) {
        startingLine = true
      } else {
        startingLine = false
      }
    }

    //override def write(Array[Byte] b, off: Int, len: Int) : Unit = {
    //  // TODO optimize the common case here....
    //}
  }

  private def wrapOutputStream(outputStream: OutputStream, prefixAnsi: Array[Byte]) : PrintStream = {
    val ansiStream = new AnsiOutputStream(outputStream, prefixAnsi)
    new PrintStream(ansiStream)
  }
}
