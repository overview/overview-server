package org.overviewproject.runner

import java.io.{ByteArrayOutputStream,File,FileOutputStream}
import java.sql.{Connection,DriverManager,ResultSet}
import java.util.concurrent.TimeoutException
import scala.concurrent.{Await,Future,blocking}
import scala.concurrent.duration.Duration
import scala.language.reflectiveCalls

import org.overviewproject.runner.commands.{Command,JvmCommand,JvmCommandWithAppendableClasspath,PostgresCommand}

object Hack {
  lazy val isDevMode = getClass.getResource("/postgresql.conf").getProtocol() == "file"
}

object Flags {
  val DatabaseUrl = s"""-Ddatasource.default.url=postgres://overview:overview@localhost:9010/overview${if (Hack.isDevMode) "-dev" else ""}"""
  val DatabaseDriver = "-Ddatasource.default.driver=org.postgresql.Driver"
  val ApolloBase = "-Dapollo.base=message-broker"
  val SearchCluster = "DevSearchIndex"
}

case class DaemonInfo(id: String, colorCode: String, command: Command)

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
  private val cmds = if (Hack.isDevMode) commands.development else commands.production

  val allDaemonInfos = Seq[DaemonInfo](
    // Seq so we won't start them in a quasi-random order. (You never know if
    // we'll hit a terrible bug that only affects one developer....)
    //
    // In theory, the order should not matter: all daemons should eventually
    // do their jobs if all are running.

    DaemonInfo("search-index", Console.BLUE, cmds.searchIndex),
    DaemonInfo("message-broker", Console.YELLOW, cmds.messageBroker),
    DaemonInfo("overview-server", Console.GREEN, cmds.webServer),
    DaemonInfo("documentset-worker", Console.CYAN, cmds.documentSetWorker),
    DaemonInfo("worker", Console.MAGENTA, cmds.worker)
  )
}

class Main(conf: Conf) {
  lazy val logger = new Logger(System.out, System.err)
  private val cmds = if (Hack.isDevMode) commands.development else commands.production

  val daemonInfos = conf.daemonInfos

  def runEvolutions: Unit = {
    logger.out.println("Running evolutions...")

    val subLogger = logger.sublogger("evolutions", Some(Console.BLUE.getBytes()))
    val daemon = new Daemon(subLogger, cmds.runEvolutions)
    val statusCode = Await.result(daemon.waitFor, Duration.Inf)
    if (statusCode != 0) {
      subLogger.err.println(s"evolutions exited with code ${statusCode}. Please fix the error.")
      System.exit(statusCode)
    }
  }

  /** Returns classpaths, one per daemonInfo, in the same order as daemonInfos.
    */
  def getClasspaths(daemonIds: Seq[String]) : Seq[Seq[String]] = {
    if (daemonIds.isEmpty) {
      return Seq()
    } else {
      logger.out.println("Compiling and fetching...")

      val cpStream = new ByteArrayOutputStream()
      val cpLogger = new Logger(new BiOutputStream(System.out, cpStream), System.err)
      val sublogger = cpLogger.sublogger("sbt", Some(Console.BLUE.getBytes()))

      val sbtTasks = daemonIds.map { s: String => s"${s}/compile; show ${s}/full-classpath" }
      val sbtCommand = "; " + sbtTasks.mkString("; ")

      val sbtRun = new Daemon(sublogger, cmds.sbt(sbtCommand))
      val statusCode = Await.result(sbtRun.waitFor, Duration.Inf)

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
      LinePattern.findAllMatchIn(outputString).map(_.group(1))
        .map { line => PathPattern.findAllMatchIn(line).map(_.group(1)).toSeq }
        .toSeq
    }
  }

  /** Waits for all daemons to finish.
    *
    * @return Status code of the first daemon to finish.
    */
  private def waitForDaemons(daemons: Seq[Daemon]) : Int = {
    import scala.concurrent.ExecutionContext.Implicits.global

    // Block and wait for status codes.
    //
    // Usually the user uses Ctrl-C to kill the process group (which will
    // automatically signal all children to exit). We wait for that to happen
    // here -- this loop blocks.
    //
    // Also: we have to support Ctrl+D, because Play outputs a log message
    // suggesting users should use it.

    val superServer = new SuperServer(daemons.toSet)

    val userPressedCtrlD = Future[Unit] { blocking {
      def isEOF(b: Int) : Boolean = (b == -1 || b == 4)
      while (!isEOF(System.in.read())) {
        // keep reading...
      }
      logger.out.println("Ctrl+D pressed.")
    }}

    val logExit = (daemon: Daemon, statusCode: Int) => {
      daemon.logger.out.println(s"Exited with status code ${statusCode}.")
      Unit
    }

    val all = Future.firstCompletedOf(
      Seq(
        superServer.waitForFirst.map((_) => Unit),
        userPressedCtrlD
      )
    ).flatMap { case _ =>
      logger.out.println("Killing processes...")
      superServer.notCompleted.map { (daemon: Daemon) => 
        daemon.logger.out.println("Sending kill signal")
        daemon.destroy()
      }
      superServer.waitForAll
    }

    val allDone = Await.result(all, Duration.Inf)

    allDone.foreach(logExit.tupled)

    superServer.waitForFirst.value.get.get._2
  }

  def run() = {
    def maybeWithDatabase[A](block: () => A) : Either[String,A] = {
      if (conf.withDatabase()) {
        val database = new Database(
          new File("database"),
          getClass.getResourceAsStream("/postgresql.conf"),
          logger.sublogger("database", Some(Console.BLACK.getBytes())).treatingErrorsAsInfo
        )
        database.withDatabase { () =>
          runEvolutions
          block()
        }
      } else {
        Right(block())
      }
    }

    def makeDaemon(spec: DaemonInfo, classpath: Seq[String]) : Daemon = {
      val command = spec.command match {
        case c: JvmCommandWithAppendableClasspath => c.withClasspath(classpath).with32BitSafe
        case c: JvmCommand => c.with32BitSafe
        case c: Command => c
      }
      val sublogger = logger.sublogger(spec.id, Some(spec.colorCode.getBytes()))
      new Daemon(sublogger, command)
    }

    logger.out.println(s"Preparing to start ${daemonInfos.map(_.id).mkString(", ")}")

    val status = maybeWithDatabase { () =>
      // Start all the daemons (except for Postgres) at once.
      //
      // (There's no good reason not to start them simultaneously. Our system
      // needs to recover gracefully in each component.)
      val appendableDaemonInfos = daemonInfos.flatMap { daemonInfo =>
        daemonInfo.command match {
          case c: JvmCommandWithAppendableClasspath => Some(daemonInfo)
          case _ => None
        }
      }
      val nonAppendableDaemonInfos = daemonInfos.flatMap { daemonInfo =>
        daemonInfo.command match {
          case c: JvmCommandWithAppendableClasspath => None
          case _ => Some(daemonInfo)
        }
      }
      // If there's an sbt task, run it too.
      val sbtDaemonInfo = Seq(conf.sbtTask.get.getOrElse(""))
        .filter(!_.isEmpty)
        .map((sbtTask: String) => DaemonInfo("sbt-task", Console.YELLOW, cmds.sbt(sbtTask)))
      val shDaemonInfo = Seq(conf.shTask.get.getOrElse(""))
        .filter(!_.isEmpty)
        .map((shTask: String) => DaemonInfo("sh-task", Console.YELLOW, cmds.sh(shTask)))

      // Actually start them up
      val appendedDaemons = appendableDaemonInfos.zip(getClasspaths(appendableDaemonInfos.map(_.id)))
        .map { case (info, classpath) => makeDaemon(info, classpath) }
      val nonAppendedDaemons = nonAppendableDaemonInfos.map(makeDaemon(_, Seq()))
      val cmdLineDaemons = (sbtDaemonInfo ++ shDaemonInfo).map(makeDaemon(_, Seq()))

      // Now we can listen to them together. We'll exit when any one exits.
      val daemons = appendedDaemons ++ nonAppendedDaemons ++ cmdLineDaemons

      // Let's open a browser window at the first opportunity
      if ((appendableDaemonInfos ++ nonAppendableDaemonInfos).map(_.id).contains("overview-server")) {
        val browserLauncher = BrowserLauncher("http://localhost:9000")
        val browserThread = new Thread(browserLauncher.createRunnable)
        browserThread.setDaemon(true)
        browserThread.start()
      }

      val statusCode = waitForDaemons(daemons)

      logger.out.println(s"All processes exited. (First exit status code was ${statusCode}. Shutting down.")

      statusCode
    }

    status match {
      case Left(msg) =>
        logger.err.println(msg)
        System.exit(1)
      case Right(statusCode) =>
        System.exit(statusCode)
    }
  }
}

object Main {
  def main(args: Array[String]) : Unit = {
    val conf = new Conf(DaemonInfoRepository, args)
    new Main(conf).run()
  }
}
