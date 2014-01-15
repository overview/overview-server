package org.overviewproject.runner

import java.io.{ByteArrayOutputStream,File}
import java.util.concurrent.TimeoutException
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Flags {
  val DatabaseUrl = "-Ddatasource.default.url=postgres://overview:overview@localhost/overview-dev"
  val DatabaseDriver = "-Ddatasource.default.driver=org.postgresql.Driver"
  val ApolloBase = "-Dapollo.base=message-broker"
  val SearchCluster = "DevSearchIndex"
}

case class DaemonInfo(
    id: String,
    colorCode: String,
    env: Seq[(String,String)], 
    jvmArgs: Seq[String],
    args: Seq[String]) {
}

trait DaemonInfoRepository {
  val allDaemonInfos : Seq[DaemonInfo]

  lazy val validKeys : Set[String] = allDaemonInfos.map(_.id).toSet

  def daemonInfosExcept(keys: Set[String]) : Seq[DaemonInfo] = {
    allDaemonInfos.filter(ds => !keys.contains(ds.id))
  }

  def daemonInfosOnly(keys: Set[String]) : Seq[DaemonInfo] = {
    allDaemonInfos.filter(ds => keys.contains(ds.id))
  }
}

object DaemonInfoRepository extends DaemonInfoRepository {
  private val sbtLaunchUri = getClass.getResource("/sbt-launch.jar").toURI()
  val sbtLaunchPath = new File(sbtLaunchUri).getAbsolutePath()

  val allDaemonInfos = Seq[DaemonInfo](
    // Seq so we won't start them in a quasi-random order. (You never know if
    // we'll hit a terrible bug that only affects one developer....)
    //
    // In theory, the order should not matter: all daemons should eventually
    // do their jobs if all are running.
    DaemonInfo(
      "search-index",
      Console.BLUE,
      Seq(),
      Seq("-Xms1g", "-Xmx1g", "-Xss256k", "-XX:+UseParNewGC", "-XX:+UseConcMarkSweepGC",
        "-XX:CMSInitiatingOccupancyFraction=75", "-XX:+UseCMSInitiatingOccupancyOnly", "-Djava.awt.headless=true", "-Delasticsearch",
        "-Des.foreground=yes", "-Des.path.home=./search-index"),
      Seq("org.elasticsearch.bootstrap.ElasticSearch")
    ),
    DaemonInfo(
      "message-broker",
      Console.YELLOW,
      Seq(),
      Seq(Flags.ApolloBase),
      Seq(
        "org.apache.activemq.apollo.boot.Apollo",
        "documentset-worker/lib",
        "org.apache.activemq.apollo.cli.Apollo",
        "run"
      )
    ),
    DaemonInfo(
      "overview-server",
      // We run "overview-server/run" through sbt. That lets it reload files
      // as they're edited.
      Console.GREEN,
      Seq(),
      Seq(
        "-XX:MaxPermSize=512M",
        "-Dpidfile.enabled=false",
        "-DapplyEvolutions.default=true" // So overview-worker works on first launch
      ),
      Seq(
        "-jar", sbtLaunchPath,
        "run"
      )
    ),
    DaemonInfo(
      "documentset-worker",
      Console.CYAN,
      Seq(),
      Seq(Flags.DatabaseUrl, Flags.DatabaseDriver, "-Dlogback.configurationFile=workerdevlog.xml"),
      Seq("org.overviewproject.DocumentSetWorker")
    ),
    DaemonInfo(
      "worker",
      Console.MAGENTA,
      Seq(),
      Seq(Flags.DatabaseUrl, Flags.DatabaseDriver, "-Dlogback.configurationFile=workerdevlog.xml", "-Xmx2g"),
      Seq("JobHandler")
    )
  )
}

class Main(conf: Conf) {
  lazy val logger = new Logger(System.out, System.err)

  val daemonInfos = conf.daemonInfos
  logger.out.println(s"Preparing to start ${daemonInfos.map(_.id).mkString(", ")}")

  /** Returns classpaths, one per daemonInfo, in the same order as daemonInfos.
   */
  def getClasspaths() : Seq[String] = {
    logger.out.println("Compiling and fetching...")

    val cpStream = new ByteArrayOutputStream()
    val cpLogger = new Logger(new BiOutputStream(System.out, cpStream), System.err)
    val sublogger = cpLogger.sublogger("sbt", Some(Console.BLUE.getBytes()))

    val sbtTasks = daemonInfos.map((spec: DaemonInfo) => s"show ${spec.id}/full-classpath")
    val sbtCommand = (Seq("", "all/compile") ++ sbtTasks).mkString("; ")

    val sbtRun = new Daemon(sublogger.toProcessLogger, Seq(),
      Seq(
        "-Dsbt.log.format=false",
        "-XX:MaxPermSize=512M",
        "-Xmx2g"
      ),
      Seq(
        "-jar", DaemonInfoRepository.sbtLaunchPath,
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
    def makeDaemon(spec: DaemonInfo, classpath: String) : Daemon = {
      new Daemon(
        logger.sublogger(spec.id, Some(spec.colorCode.getBytes())).toProcessLogger,
        spec.env,
        spec.jvmArgs ++ (if (spec.id == "overview-server") Seq() else Seq("-cp", classpath)),
        spec.args
      )
    }

    val classpaths = getClasspaths()

    // Start all the daemons
    val daemons = daemonInfos.zip(classpaths).map {
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
    //
    // Also: we have to support Ctrl+D, because Play outputs a log message
    // suggesting users should use it.

    var shuttingDown = false

    def waitForDaemonToExitOrCtrlD(daemon: Daemon) : Unit = {
      import scala.concurrent.ExecutionContext.Implicits.global
      while (true) {
        // Spin through input looking for Ctrl+D
        if (!shuttingDown) {
          def isEOF(b: Int) : Boolean = (b == -1 || b == 4)
          while (System.in.available() > 0) {
            if (isEOF(System.in.read())) {
              System.out.println("Ctrl+D pressed. Killing processes...")
              shuttingDown = true
            }
          }
        }

        if (shuttingDown) daemon.process.destroy()

        // Is the daemon dead? If so, break so we get to the next daemon
        try {
          val duration = Duration(100, "ms")
          val statusCode = Await.result(daemon.statusCodeFuture, duration)
          daemon.logger.out(s"Process exited with status code ${statusCode}\n")
          return
        } catch {
          case _: TimeoutException => Unit // Daemon's still up. Go back to reading inputs.
        }
      }
    }

    daemons.foreach(waitForDaemonToExitOrCtrlD)

    logger.out.println("All processes exited. Shutting down.")
  }
}

object Main {
  def main(args: Array[String]) : Unit = {
    val conf = new Conf(DaemonInfoRepository, args)
    new Main(conf).run()
  }
}
