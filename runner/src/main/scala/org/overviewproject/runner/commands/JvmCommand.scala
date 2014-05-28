package org.overviewproject.runner.commands

import java.io.File

/** A command that uses Java.
  *
  * If you were running this command from the shell, it would look like:
  *
  *     ENV1=val1 ENV2=val2 \$JAVA_HOME/bin/java \$jvmArg1 \$jvmArg2 \$arg1 \$arg2
  *
  * The key feature of this class: it finds the Java executable for you, based
  * on the current JVM's system properties.
  */
class JvmCommand(
    override val env: Seq[(String,String)],
    val jvmArgs: Seq[String],
    val appArgs: Seq[String]
  ) extends Command {

  override def argv = Seq(JvmCommand.javaPath) ++ jvmArgs ++ appArgs

  /** Copies this JvmCommand, adding jvmArgs from systemProperties.
    *
    * For instance, if systemProperties includes
    * <tt>("frontend.jvm.Xmx1400M" -> "",
    * "frontend.jvm.cp" -> "/foo/bar.jar",
    * "frontend.props.overview.multi_user" -> "false")</tt>, then four extra
    * <tt>jvmArgs</tt> will be appended to the command: <tt>"-Xmx1400M"</tt>,
    * <tt>"-cp"</tt>, <tt>"/foo/bar.jar"</tt> and
    * <tt>-Dfrontend.app.overview.multi_user=false</tt>.
    */
  def withPropsByNamespace(systemProperties: Map[String,String], namespace: String) : JvmCommand = {
    val jvmPattern = (namespace + "\\.jvm\\.(.*)").r
    val propPattern = (namespace + "\\.props\\.(.*)").r

    val jvmArgsFromProps = systemProperties
      .toIterable
      .map { case (k: String, v: String) => 
        k match {
          case jvmPattern(m) =>
            if (v == "") Seq("-" + m) else Seq("-" + m, v)
          case propPattern(m) => Seq("-D" + m + "=" + v)
          case _ => Seq()
        }
      }
      .flatten

    new JvmCommand(env, jvmArgs ++ jvmArgsFromProps, appArgs)
  }

  def withPropsByNamespace(namespace: String) : JvmCommand = withPropsByNamespace(scala.sys.props.toMap, namespace)

  /** If the argument matches -Xm[nsx]NUM, and NUM is too high, returns the
    * same string with NUM reduced. Otherwise, returns the passed argument.
    */
  private def reduceArgumentHeapSizeTo32BitSafe(arg: String) : String = {
    val MemSizePattern = """(-Xm[nsx])(\d+)([gGkKmM]?)""".r

    arg match {
      case MemSizePattern(prefix, num, suffix) => {
        val multiplier = suffix match {
          case "k" => 1024
          case "K" => 1024
          case "m" => 1024*1024
          case "M" => 1024*1024
          case "g" => 1024*1024*1024
          case "G" => 1024*1024*1024
          case _ => 1
        }
        val size = multiplier * num.toLong

        if (size > JvmCommand.Max32BitHeapSize) {
          s"${prefix}${JvmCommand.Max32BitHeapSizeString}"
        } else {
          arg
        }
      }
      case _ => arg
    }
  }

  private def is32BitMarker(is32Bit: Boolean) : Seq[String] = {
    if (is32Bit) {
      Seq("-Doverview.is32BitJava=true")
    } else {
      Seq()
    }
  }

  private[commands] def with32BitSafe(is64Bit: Boolean): JvmCommand = {
    if (is64Bit) {
      this
    } else {
      new JvmCommand(
        env,
        jvmArgs.map(reduceArgumentHeapSizeTo32BitSafe(_)) ++ is32BitMarker(!is64Bit),
        appArgs
      )
    }
  }

  /** Return a version of the command with heap sizes decreased to 1.4GB if
    * they are higher and the platform is 32-bit.
    */
  def with32BitSafe: JvmCommand = with32BitSafe(JvmCommand.is64Bit)
}

object JvmCommand {
  private val javaPath: String = {
    val home = new File(System.getProperty("java.home"))
    new File(new File(home, "bin"), "java").getAbsolutePath
  }

  /** `true` iff this is a 64-bit version of Java.
    *
    * This works by running "java -d64 -version". 32-bit Java will exit with
    * a nonzero status code; 64-bit Java will exit with a zero status code.
    */
  private[commands] lazy val is64Bit: Boolean = {
    import scala.sys.process.{ Process, ProcessLogger }

    val pb = Process(Seq(javaPath, "-d64", "-version"))

    val retval = pb!(ProcessLogger(line => Unit, line => Unit))
    retval == 0
  }

  private val Max32BitHeapSize : Long = 1363148800
  private val Max32BitHeapSizeString : String = "1300m"
}
