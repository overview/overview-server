package org.overviewproject.runner

import java.io.File
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.sys.process.{ Process, ProcessBuilder, ProcessLogger }

class DaemonSpec extends Specification with Mockito {
  lazy val TestAppJar : File = { // lazy so the error message is nicer
    val url = Thread.currentThread().getContextClassLoader().getResource("TestApp.jar")
    new File(url.getPath())
  }
  val TestDuration = Duration(2, SECONDS)

  trait Base extends Scope {
    trait EverythingLogger {
      def out(s: String) : Unit
      def err(s: String) : Unit
    }
    val log = mock[EverythingLogger]
    val processLogger = new ProcessLogger {
      override def buffer[T](f: => T) : T = f
      def out(s: => String) { log.out(s) }
      def err(s: => String) { log.err(s) }
    }

    def daemonEnv : Seq[(String,String)] = Seq()
    def daemonJvmArgs : Seq[String] = Seq()
    def daemonCommand : Seq[String] = Seq("-jar", TestAppJar.getAbsolutePath)
    def buildDaemon : Daemon = new Daemon(processLogger, daemonEnv, daemonJvmArgs, daemonCommand)

    lazy val daemon = buildDaemon

    // Waits for process to complete and returns status code
    def run() : Int = {
      Await.result(daemon.statusCodeFuture, TestDuration)
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
      daemon.statusCodeFuture.isCompleted must beEqualTo(false)
      run() // reap the process
    }

    "log stdout" in new Base {
      run()
      there was one(log).out("This is on stdout")
    }

    "log stderr" in new Base {
      run()
      there was one(log).err("This is on stderr")
    }

    "set environment variables" in new Base {
      override def daemonEnv = Seq("FOO" -> "bar")
      run()
      there was one(log).out("ENV: FOO=bar")
    }

    "set JVM args" in new Base {
      override def daemonJvmArgs = Seq("-Xmx128m")
      run()
      there was one(log).out("VMARG: -Xmx128m")
    }

    "set args" in new Base {
      override def daemonCommand = super.daemonCommand ++ Seq("arg1", "arg2")
      run()
      there was one(log).out("ARG: arg1")
      there was one(log).out("ARG: arg2")
    }
  }
}
