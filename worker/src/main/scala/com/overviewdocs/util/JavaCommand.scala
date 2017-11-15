package com.overviewdocs.util

import java.nio.file.Paths

object JavaCommand {
  // Revolver ("reStart", dev mode) uses "-Xbootclasspath/a", which doesn't
  // appear in java.class.path
  private val BootClassPathRegex = """^.*-classpath (.*) com.overviewdocs.Worker$""".r

  private val classPath: String = Option[String](System.getProperty("sun.java.command")) match {
    case Some(BootClassPathRegex(cp)) => cp
    case _ => System.getProperty("java.class.path")
  }
  private val javaPath: String = Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java").toString

  def apply(args: String*): Seq[String] = Seq(
    javaPath,
    "-Dfile.encoding=UTF-8",
    "-Duser.timezone=UTC",
    "-cp",
    classPath
  ) ++ args
}
