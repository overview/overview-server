package org.overviewproject.runner.commands

import org.specs2.mutable.Specification

class WithAppendableClasspathSpec extends Specification {
  "WithAppendableClasspath" should {
    "add a classpath arg" in {
      val jvmCommand = new JvmCommandWithAppendableClasspath(Seq(), Seq("jvmArg1"), Seq())
      val newJvmCommand : JvmCommand with WithAppendableClasspath = jvmCommand.withClasspath(Seq("path1", "path2"))
      newJvmCommand.jvmArgs must beEqualTo(Seq("jvmArg1", "-cp", "path1:path2"))
    }
  }
}
