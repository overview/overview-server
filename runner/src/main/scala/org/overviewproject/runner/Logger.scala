package org.overviewproject.runner

import java.io.{ FilterOutputStream, OutputStream, PrintStream }
import scala.sys.process.ProcessLogger

/** Provides a simple logging interface:
  *
  *     logger.out.println("String")
  *     logger.err.println("String")
  */
trait StdLogger {
  val out: PrintStream
  val err: PrintStream
}

class Logger(private val baseOut: OutputStream, private val baseErr: OutputStream, val prefixAnsi: Option[Array[Byte]] = None) extends StdLogger {
  val outAnsiPrefix = prefixAnsi.getOrElse(Array[Byte]())
  val errAnsiPrefix = Array.concat("\u001b[31;1mERROR - \u001b[0m".getBytes(), prefixAnsi.getOrElse(Array[Byte]()))

  override val out = Logger.wrapOutputStream(baseOut, outAnsiPrefix)
  override val err = Logger.wrapOutputStream(baseErr, errAnsiPrefix)

  def sublogger(newPrefix : String, newPrefixAnsiCode : Option[Array[Byte]]): Logger = {
    val newPrefixAnsi = newPrefixAnsiCode match {
      case Some(code) => Array.concat(code, s"[${newPrefix}] ".getBytes(), Console.RESET.getBytes())
      case None => s"[${newPrefix}] ".getBytes()
    }

    new Logger(baseOut, baseErr, Some(newPrefixAnsi))
  }

  def treatingErrorsAsInfo: StdLogger = {
    val self = this
    new StdLogger {
      override val out = self.out
      override val err = self.out
    }
  }
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
