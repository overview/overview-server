package org.overviewproject.runner

import java.io.File
import java.io.{ByteArrayOutputStream,PrintStream}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.sys.process.{ Process, ProcessBuilder, ProcessLogger }

import org.overviewproject.runner.commands.{ Command, JvmCommand }

class DaemonSpec extends Specification {
  lazy val TestAppJar : File = { // lazy so the error message is nicer
    val url = Thread.currentThread().getContextClassLoader().getResource("TestApp.jar")
    new File(url.getPath())
  }
  lazy val TestDaemonJar : File = { // lazy so error message is nicer
    val url = Thread.currentThread().getContextClassLoader().getResource("TestDaemon.jar")
    new File(url.getPath())
  }

  val TestDuration = Duration(3, SECONDS)

  trait Base extends Scope {
    class ByteArrayPrintStream(val byteArrayOutputStream: ByteArrayOutputStream) extends PrintStream(byteArrayOutputStream) {
      override def toString: String = byteArrayOutputStream.toString
    }
    object ByteArrayPrintStream {
      def apply() = new ByteArrayPrintStream(new ByteArrayOutputStream)
    }

    val log = new StdLogger {
      override val out = ByteArrayPrintStream()
      override val err = ByteArrayPrintStream()
    }

    def daemonCommandEnv : Seq[(String,String)] = Seq()
    def daemonCommandJvmArgs : Seq[String] = Seq()
    def daemonCommandArgs : Seq[String] = Seq("-jar", TestAppJar.getAbsolutePath)
    def daemonCommand : Command = new JvmCommand(daemonCommandEnv, daemonCommandJvmArgs, daemonCommandArgs)
    def buildDaemon : Daemon = new Daemon(log, daemonCommand)

    lazy val daemon = buildDaemon

    // Waits for process to complete and returns status code
    def run() : Int = {
      Await.result(daemon.waitFor, TestDuration)
    }
  }

  "Daemon" should {
    "resolve status code when completed" in new Base {
      val statusCode = run()
      statusCode must beEqualTo(4)
    }

    "start asynchronously" in new Base {
      // If there is no return value after the daemon was started, then we're
      // async.
      daemon.waitFor.isCompleted must beEqualTo(false)
      run() // reap the process
    }

    "log stdout" in new Base {
      run()
      log.out.toString must contain("This is on stdout")
    }

    "log stderr" in new Base {
      run()
      log.err.toString must contain("This is on stderr")
    }

    "set environment variables" in new Base {
      override def daemonCommandEnv = Seq("FOO" -> "bar")
      run()
      log.out.toString must contain("ENV: FOO=bar")
    }

    "set JVM args" in new Base {
      override def daemonCommandJvmArgs = Seq("-Xmx128m")
      run()
      log.out.toString must contain("VMARG: -Xmx128m")
    }

    "set args" in new Base {
      override def daemonCommandArgs = super.daemonCommandArgs ++ Seq("arg1", "arg2")
      run()
      log.out.toString must contain("ARG: arg1")
      log.out.toString must contain("ARG: arg2")
    }

    "kill gracefully" in new Base {
      override def daemonCommandArgs : Seq[String] = Seq("-jar", TestDaemonJar.getAbsolutePath)

      var threwException = false

      val exceptionHandler = new Thread.UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, e: Throwable) = {
          threwException = true
        }
      }

      daemon.outLogger.setUncaughtExceptionHandler(exceptionHandler)
      Thread.sleep(100) // give time for logging threads to start up

      // At this point, the logging threads will be running, blocking in
      // BufferedReader.readLine().
      //
      // Now, let's cause an IOException!
      daemon.destroy

      run() // wait for the kill to finish

      log.out.toString must not contain("not killed")
      threwException must beEqualTo(false)
    }
  }
}
