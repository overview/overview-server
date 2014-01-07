package org.overviewproject.runner

import java.io.{ByteArrayOutputStream,File,FilterOutputStream,OutputStream}
import org.rogach.scallop.{ArgType,ScallopConf,ValueConverter}
import scala.reflect.runtime.universe.typeTag
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Flags {
  val DatabaseUrl = "-Ddatasource.default.url=postgres://overview:overview@localhost/overview-dev"
  val DatabaseDriver = "-Ddatasource.default.driver=org.postgresql.Driver"
  val ApolloBase = "-Dapollo.base=message-broker"
  val SearchCluster = "DevSearchIndex"
}

case class DaemonSpec(
    key: String,
    colorCode: String,
    env: Seq[(String,String)], 
    jvmArgs: Seq[String],
    args: Seq[String]) {
}

object DaemonSpecs {
  val allDaemonSpecs = Seq[DaemonSpec](
    // Seq so we won't start them in a quasi-random order. (You never know if
    // we'll hit a terrible bug that only affects one developer....)
    //
    // In theory, the order should not matter: all daemons should eventually
    // do their jobs if all are running.
    DaemonSpec(
      "search-index",
      Console.BLUE,
      Seq(),
      Seq("-Xms1g", "-Xmx1g", "-Xss256k", "-XX:+UseParNewGC", "-XX:+UseConcMarkSweepGC",
        "-XX:CMSInitiatingOccupancyFraction=75", "-XX:+UseCMSInitiatingOccupancyOnly", "-Djava.awt.headless=true", "-Delasticsearch",
        "-Des.foreground=yes", "-Des.path.home=./search-index"),
      Seq("org.elasticsearch.bootstrap.ElasticSearch")
    ),
    DaemonSpec(
      "message-broker",
      Console.YELLOW,
      Seq(),
      Seq(Flags.ApolloBase),
      Seq("org.apache.activemq.apollo.boot.Apollo", "documentset-worker/lib", "org.apache.activemq.apollo.cli.Apollo", "run")
    ),
    DaemonSpec(
      "overview-server",
      Console.GREEN,
      Seq(),
      Seq(
        "-Dpidfile.enabled=false",
        "-DapplyEvolutions.default=true" // So overview-worker works on first launch
      ),
      Seq("play.core.server.NettyServer", ".")
    ),
    DaemonSpec(
      "documentset-worker",
      Console.CYAN,
      Seq(),
      Seq(Flags.DatabaseUrl, Flags.DatabaseDriver, "-Dlogback.configurationFile=workerdevlog.xml"),
      Seq("org.overviewproject.DocumentSetWorker")
    ),
    DaemonSpec(
      "worker",
      Console.MAGENTA,
      Seq(),
      Seq(Flags.DatabaseUrl, Flags.DatabaseDriver, "-Dlogback.configurationFile=workerdevlog.xml", "-Xmx2g"),
      Seq("JobHandler")
    )
  )

  val validKeys : Set[String] = allDaemonSpecs.map(_.key).toSet

  def daemonSpecsExcept(keys: Set[String]) : Seq[DaemonSpec] = {
    allDaemonSpecs.filter(ds => !keys.contains(ds.key))
  }

  def daemonSpecsOnly(keys: Set[String]) : Seq[DaemonSpec] = {
    allDaemonSpecs.filter(ds => keys.contains(ds.key))
  }
}

/** Command-line argument parser.
  *
  * Usage:
  *
  *   val conf = new Conf(arguments)
  *   val daemonSpecs = conf.daemonSpecs
  */
class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version("Overview Development Version Runner")
  banner(s"""Usage: run [OPTION]
            |By default, runs all servers. You may specify --only-servers or
            |--except-servers if you want to avoid some.
            |
            |Servers: ${DaemonSpecs.allDaemonSpecs.map(_.key).mkString(",")}
            |
            |Options:
            |""".stripMargin)

  def daemonSpecListConverter = new ValueConverter[Seq[DaemonSpec]] {
    def parse(s: List[(String, List[String])]) : Either[String, Option[Seq[DaemonSpec]]] = {
      if (s.isEmpty) {
        Right(None)
      } else {
        val isOnly = s(0)._1 == "only-servers"
        val keys = s.map(_._2).flatten.mkString(",").split(',').toSet
        val invalidKeys = keys -- DaemonSpecs.validKeys

        if (invalidKeys.isEmpty) {
          val specs = if (isOnly) DaemonSpecs.daemonSpecsOnly(keys) else DaemonSpecs.daemonSpecsExcept(keys)
          Right(Some(specs))
        } else {
          Left(s"Please specify a comma-separated list of valid servers, among these: ${DaemonSpecs.validKeys.mkString(",")}")
        }
      }
    }

    val tag = typeTag[Seq[DaemonSpec]]
    val argType = ArgType.SINGLE
  }

  val onlyServers = opt[Seq[DaemonSpec]]("only-servers", descr="Only start this comma-separated list of servers")(daemonSpecListConverter)
  val exceptServers = opt[Seq[DaemonSpec]]("except-servers", descr="Start all but this comma-separated list of servers")(daemonSpecListConverter)

  mutuallyExclusive(onlyServers, exceptServers)

  def daemonSpecs : Seq[DaemonSpec] = {
    onlyServers.get
      .orElse(exceptServers.get)
      .getOrElse(DaemonSpecs.allDaemonSpecs)
  }
}

class BiOutputStream(out1: OutputStream, out2: OutputStream) extends FilterOutputStream(out1) {
  override def write(b: Int): Unit = {
    super.write(b)
    out2.write(b)
  }

  override def write(b: Array[Byte]): Unit = {
    super.write(b)
    out2.write(b)
  }

  override def write(b: Array[Byte], off: Int, len: Int) : Unit = {
    super.write(b, off, len)
    out2.write(b, off, len)
  }

  override def flush(): Unit = {
    super.flush()
    out2.flush()
  }

  override def close(): Unit = {
    super.close()
    out2.close()
  }
}

class Main(conf: Conf) {
  lazy val logger = new Logger(System.out, System.err)

  val daemonSpecs = conf.daemonSpecs
  logger.out.println(s"Preparing to start ${daemonSpecs.map(_.key).mkString(", ")}")

  /** Returns classpaths, one per daemonSpec, in the same order as daemonSpecs.
   */
  def getClasspaths() : Seq[String] = {
    logger.out.println("Compiling and fetching...")

    val cpStream = new ByteArrayOutputStream()
    val cpLogger = new Logger(new BiOutputStream(System.out, cpStream), System.err)
    val sublogger = cpLogger.sublogger("sbt", Some(Console.BLUE.getBytes()))

    val sbtTasks = daemonSpecs.map((spec: DaemonSpec) => s"show ${spec.key}/full-classpath")
    val sbtCommand = (Seq("", "all/compile") ++ sbtTasks).mkString("; ")

    val sbtLaunchUrl = getClass.getResource("/sbt-launch.jar")

    val sbtRun = new Daemon(sublogger.toProcessLogger, Seq(),
      Seq(
        "-Dsbt.log.format=false"
      ),
      Seq(
        "-jar", new File(sbtLaunchUrl.toURI()).getAbsolutePath(),
        sbtCommand
      )
    )
    val statusCode = Await.result(sbtRun.statusCodeFuture, Duration.Inf)

    if (statusCode != 0) {
      cpLogger.err.println(s"sbt exited with code ${statusCode}. Please fix the error.")
      System.exit(statusCode)
    }

    // Find lines like [info] List(Attributed(path1), Attributed(path2), ...).
    // There will be one line per "show KEY/full-classpath" command.
    // Parse each one and return path1:path2:...
    val LinePattern = """\[info\] List\((.*)\)""".r
    val PathPattern = """Attributed\(([^\)]+)\)""".r
    val outputString = new String(cpStream.toByteArray())
    System.out.println("OUTPUT: " + outputString)
    LinePattern.findAllMatchIn(outputString).map(_.group(1))
      .map { line => PathPattern.findAllMatchIn(line).map(_.group(1)).mkString(":") }
      .toSeq
  }

  def run() = {
    def makeDaemon(spec: DaemonSpec, classpath: String) : Daemon = {
      new Daemon(
        logger.sublogger(spec.key, Some(spec.colorCode.getBytes())).toProcessLogger,
        spec.env,
        spec.jvmArgs ++ Seq("-cp", classpath),
        spec.args
      )
    }

    val classpaths = getClasspaths()

    // Start all the daemons
    val daemons = daemonSpecs.zip(classpaths).map {
      case (spec, classpath) => makeDaemon(spec, classpath)
    }

    // Block and wait for status codes.
    //
    // Usually the user uses Ctrl-C to kill the process; that will happen
    // here, before any status codes are returned. That's the real purpose
    // of this loop: to block, not to inform.
    //
    // Note that we only listen for one status code at a time: these tasks
    // block a lot, so if we listened for them all at once we'd exhaust the
    // thread pool.
    import scala.concurrent.ExecutionContext.Implicits.global
    for (daemon <- daemons) {
      val statusCode = Await.result(daemon.statusCodeFuture, Duration.Inf)
      daemon.logger.out("This process exited with status code ${statusCode}\n")
    }

    logger.out.println(s"All processes exited. Shutting down.")
  }
}

object Main {
  def main(args: Array[String]) : Unit = {
    val conf = new Conf(args)
    new Main(conf).run()
  }
}
