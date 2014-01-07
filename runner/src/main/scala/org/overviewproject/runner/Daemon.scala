package org.overviewproject.runner

import java.io.File
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{Process, ProcessLogger}

class Daemon(
    val logger: ProcessLogger,
    env: Seq[(String,String)] = Seq(),
    jvmArgs: Seq[String] = Seq(),
    args: Seq[String] = Seq()) {

  private val commandSeq = Seq(Daemon.javaPath) ++ jvmArgs ++ args

  logger.out("Running " + commandSeq.map(x => s"'${x}'").mkString(" "))

  private val processBuilder = Process(commandSeq, None, env : _*)
  val process = processBuilder.run(logger)

  val statusCodeFuture : Future[Int] = Future({ process.exitValue() })(ExecutionContext.global)
}

object Daemon {
  private val javaPath: String = {
    val home = new File(System.getProperty("java.home"))
    new File(new File(home, "bin"), "java").getAbsolutePath
  }
}
