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
      cmd.with32BitSafe(false).jvmArgs.take(4) must beEqualTo(Seq("-Xms1300m", "-Xmx1300m", "-Xmn1300m", "-Xint"))
    }

    "add -Doverview.is32BitJava=true in with32BitSafe(false)" in {
      val cmd = new JvmCommand(Seq(), Seq("-Xfoo"), Seq())
      cmd.with32BitSafe(false).jvmArgs must beEqualTo(Seq("-Xfoo", "-Doverview.is32BitJava=true"))
    }

    "not decrease heap sizes in with32BitSafe(true)" in {
      val cmd = new JvmCommand(Seq(), Seq("-Xms3g", "-Xmx4000m", "-Xmn2000000k", "-Xint"), Seq())
      cmd.with32BitSafe(true).jvmArgs.take(4) must beEqualTo(cmd.jvmArgs)
    }

    "not add -Doverview.is32BitJava=true in with32BitSafe(true)" in {
      val cmd = new JvmCommand(Seq(), Seq("-Xfoo"), Seq())
      cmd.with32BitSafe(true).jvmArgs must beEqualTo(Seq("-Xfoo"))
    }

    "not decrease heap sizes when they are small enough already" in {
      val cmd = new JvmCommand(Seq(), Seq("-Xms1024m", "-Xmx1250m", "-Xmn200k", "-Xint"), Seq())
      cmd.with32BitSafe(false).jvmArgs.take(4) must beEqualTo(cmd.jvmArgs)
    }

    "withPropsByNamespace" should {
      "add a single JVM arg" in {
        val cmd = new JvmCommand(Seq(), Seq(), Seq()).withPropsByNamespace(Map("frontend.jvm.Xmx1400M" -> ""), "frontend")
        cmd.jvmArgs must beEqualTo(Seq("-Xmx1400M"))
      }

      "add two JVM args" in {
        val cmd = new JvmCommand(Seq(), Seq(), Seq()).withPropsByNamespace(Map("frontend.jvm.cp" -> "foo.jar"), "frontend")
        cmd.jvmArgs must beEqualTo(Seq("-cp", "foo.jar"))
      }

      "define a JVM property" in {
        val cmd = new JvmCommand(Seq(), Seq(), Seq()).withPropsByNamespace(Map("frontend.props.overview.multi_user" -> "false"), "frontend")
        cmd.jvmArgs must beEqualTo(Seq("-Doverview.multi_user=false"))
      }

      "add all three" in {
        val cmd = new JvmCommand(Seq(), Seq(), Seq()).withPropsByNamespace(Map(
          "frontend.jvm.Xmx1400M" -> "",
          "frontend.jvm.cp" -> "foo.jar",
          "frontend.props.overview.multi_user" -> "false"
        ), "frontend")
        cmd.jvmArgs must beEqualTo(Seq("-Xmx1400M", "-cp", "foo.jar", "-Doverview.multi_user=false"))
      }

      "not add a property with the wrong namespace" in {
        val cmd = new JvmCommand(Seq(), Seq(), Seq()).withPropsByNamespace(Map("foo.jvm.Xmx1400M" -> ""), "bar")
        cmd.jvmArgs must beEqualTo(Seq())
      }
    }
  }
}
