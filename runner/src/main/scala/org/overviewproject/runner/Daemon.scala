package org.overviewproject.runner

import java.io.{BufferedReader,IOException,InputStream,InputStreamReader,PrintStream}
import java.lang.Process
import scala.concurrent.{ExecutionContext, Future}

import org.overviewproject.runner.commands.Command

class Daemon(val logger: StdLogger, val command: Command) {
  logger.out.println("Running " + command)

  private val cmdArray : Array[String] = command.argv.toArray
  private val envArray : Array[String] = command.env.map { t: (String,String) => s"${t._1}=${t._2}" }.toArray

  private def createLoggingThread(inStream: InputStream, outStream: PrintStream) : Thread = {
    new Thread(new Runnable {
      override def run : Unit = {
        val reader = new BufferedReader(new InputStreamReader(inStream))

        def readNextLine : Option[String] = {
          try {
            Option(reader.readLine())
          } catch {
            case (e: IOException) if (e.getMessage() == "Stream closed") => None
          }
        }

        var nextLine: Option[String] = readNextLine
        while (nextLine.isDefined) {
          outStream.println(nextLine.get)
          nextLine = readNextLine
        }
      }
    }, command.toString)
  }

  val process: Process = Runtime.getRuntime().exec(cmdArray, envArray)
  val outLogger = createLoggingThread(process.getInputStream(), logger.out)
  val errLogger = createLoggingThread(process.getErrorStream(), logger.err)
  outLogger.start()
  errLogger.start()

  def destroyAsynchronously(): Unit = process.destroy()

  val statusCodeFuture : Future[Int] = Future({
    val result = process.waitFor()
    outLogger.join()
    errLogger.join()
    result
  })(ExecutionContext.global)
}
