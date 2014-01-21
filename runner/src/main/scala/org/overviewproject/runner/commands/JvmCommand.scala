package org.overviewproject.runner.commands

import java.io.File

/** A command that uses Java.
  *
  * If you were running this command from the shell, it would look like:
  *
  *     ENV1=val1 ENV2=val2 $JAVA_HOME/bin/java $jvmArg1 $jvmArg2 $arg1 $arg2
  *
  * The key feature of this class: it finds the Java executable for you, based
  * on the current JVM's system properties.
  */
class JvmCommand(
    override val env: Seq[(String,String)],
    val jvmArgs: Seq[String],
    val args: Seq[String]
  ) extends Command(env, Seq(JvmCommand.javaPath) ++ jvmArgs ++ args)

object JvmCommand {
  private val javaPath: String = {
    val home = new File(System.getProperty("java.home"))
    new File(new File(home, "bin"), "java").getAbsolutePath
  }
}
