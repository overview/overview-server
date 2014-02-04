package org.overviewproject.runner.commands

import org.specs2.mutable.Specification
import scala.sys.process.{Process, ProcessBuilder, ProcessLogger}

class JvmCommandSpec extends Specification {
  // Returns (retval, stdout, stderr)
  def run(cmd: Command) : (Int,String,String) = {
    var stdout = new StringBuilder()
    var stderr = new StringBuilder()

    val logger = new ProcessLogger {
      override def buffer[T](f: => T): T = f
      override def err(s: => String) : Unit = stderr.append(s + "\n")
      override def out(s: => String) : Unit = stdout.append(s + "\n")
    }

    val process = Process(cmd.argv, None, cmd.env: _*)
    val retval = process!(logger)

    (retval, stdout.toString, stderr.toString)
  }

  "JvmCommand" should {
    "find and run Java" in {
      val cmd = new JvmCommand(Seq(), Seq(), Seq("-version"))
      val (retval, stdout, stderr) = run(cmd)
      stderr must contain("java version")
    }

    "not crash when deciding whether Java is 64-bit" in {
      // We can't really test this without rewriting the test.
      //
      // All we can do is test that there is no crash.
      JvmCommand.is64Bit must beOneOf(true, false)
    }

    "decrease heap sizes in with32BitSafe(false)" in {
      val cmd = new JvmCommand(Seq(), Seq("-Xms3g", "-Xmx4000m", "-Xmn2000000k", "-Xint"), Seq())
      cmd.with32BitSafe(false).jvmArgs must beEqualTo(Seq("-Xms1536m", "-Xmx1536m", "-Xmn1536m", "-Xint"))
    }

    "not decrease heap sizes in with32BitSafe(true)" in {
      val cmd = new JvmCommand(Seq(), Seq("-Xms3g", "-Xmx4000m", "-Xmn2000000k", "-Xint"), Seq())
      cmd.with32BitSafe(true).jvmArgs must beEqualTo(cmd.jvmArgs)
    }

    "not decrease heap sizes when they are small enough already" in {
      val cmd = new JvmCommand(Seq(), Seq("-Xms1024m", "-Xmx1500m", "-Xmn200k", "-Xint"), Seq())
      cmd.with32BitSafe(false).jvmArgs must beEqualTo(cmd.jvmArgs)
    }
  }
}
