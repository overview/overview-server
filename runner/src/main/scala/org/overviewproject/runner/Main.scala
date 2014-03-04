package org.overviewproject.runner

import java.io.{ByteArrayOutputStream,File,FileOutputStream}
import java.sql.{Connection,DriverManager,ResultSet}
import java.util.concurrent.TimeoutException
import scala.concurrent.{Await,Future,blocking}
import scala.concurrent.duration.Duration
import scala.language.reflectiveCalls

import org.overviewproject.runner.commands.{Command,JvmCommand,JvmCommandWithAppendableClasspath,PostgresCommand}

object Flags {
  val DatabaseUrl = "-Ddatasource.default.url=postgres://overview:overview@localhost:9010/overview-dev"
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
  val allDaemonInfos = Seq[DaemonInfo](
    // Seq so we won't start them in a quasi-random order. (You never know if
    // we'll hit a terrible bug that only affects one developer....)
    //
    // In theory, the order should not matter: all daemons should eventually
    // do their jobs if all are running.

    DaemonInfo("database", Console.BLACK, commands.PostgresServerCommand),
    DaemonInfo("search-index", Console.BLUE, commands.SearchIndex),
    DaemonInfo("message-broker", Console.YELLOW, commands.MessageBroker),
    DaemonInfo("overview-server", Console.GREEN, commands.OverviewServer),
    DaemonInfo("documentset-worker", Console.CYAN, commands.DocumentSetWorker),
    DaemonInfo("worker", Console.MAGENTA, commands.Worker)
  )
}

class Main(conf: Conf) {
  def using[T <: { def close() }](resource: T)(block: T => Unit) {
    // http://stackoverflow.com/questions/2207425/what-automatic-resource-management-alternatives-exists-for-scala
    try {
      block(resource)
    } finally {
      if (resource != null) resource.close()
    }
  }

  def retryWithTimeout[T](currentTry: Int, timeout: Duration)(block: => T) : T = {
    try {
      block
    } catch { case e: Exception =>
      if (currentTry == 0) throw e

      Thread.sleep(timeout.toMillis)
      retryWithTimeout(currentTry - 1, timeout)(block)
    }
  }

  def getPostgresConnection: Connection = {
    retryWithTimeout(100, Duration(100, "ms")) {
      DriverManager.getConnection("jdbc:postgresql://localhost:9010/postgres?user=postgres")
    }
  }

  //def waitForPostgresServerToFinish: Unit = {
  //  retryWithTimeout(10, Duration(100, "ms")) {
  //    val file = new File(conf.databasePath, "postmaster.pid")
  //    if (file.exists) {
  //      throw new Exception(s"File ${file.getAbsolutePath} still exists")
  //    }
  //  }
  //}

  /** Runs the given block only once a Postgres server starts up and a
    * Connection is ready.
    *
    * When the block ends, Postgres will shut down.
    */
  def withPostgresServerRunning[T](logger: StdLogger)(block: (Connection) => T) {
    val daemon = new Daemon(logger, commands.PostgresServerCommand)
    try {
      using(getPostgresConnection) { conn =>
        block(conn)
      }
    } finally {
      daemon.destroy()
      val statusCode = Await.result(daemon.waitFor, Duration.Inf)
    }
  }

  lazy val logger = new Logger(System.out, System.err)

  val daemonInfos = conf.daemonInfos

  /** Returns classpaths, one per daemonInfo, in the same order as daemonInfos.
    *
    * Side-effect: connects to Postgres and runs database evolutions. (Why
    * here? Because sbt takes ages to load, so we only want to load it once.)
    */
  def getClasspathsAndRunEvolutionsAsASideEffect(daemonIds: Seq[String]) : Seq[Seq[String]] = {
    if (daemonIds.isEmpty) {
      Seq()
    } else {
      logger.out.println("Compiling and fetching...")

      val cpStream = new ByteArrayOutputStream()
      val cpLogger = new Logger(new BiOutputStream(System.out, cpStream), System.err)
      val sublogger = cpLogger.sublogger("sbt", Some(Console.BLUE.getBytes()))

      val sbtTasks = daemonIds.map { s: String => s"show ${s}/full-classpath" }
      val sbtCommand = (Seq("", "all/compile", "db-evolution-applier/run") ++ sbtTasks).mkString("; ")

      val sbtRun = new Daemon(sublogger, commands.sbt(sbtCommand).with32BitSafe)
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

  private def copyPostgresqlConf(databaseDir: File) : Unit = {
    val buf = new Array[Byte](1024 * 1024)

    using(this.getClass.getResourceAsStream("/postgresql.conf")) { is =>
      using(new FileOutputStream(new File(databaseDir, "postgresql.conf"))) { os =>
        var len = is.read(buf)
        while (len > 0) {
          os.write(buf, 0, len)
          len = is.read(buf)
        }
      }
    }
  }

  private def ensureDatabaseClusterExists() : Unit = {
    val subLogger = logger.sublogger("database", Some(Console.BLACK.getBytes())).treatingErrorsAsInfo

    val databaseDir = new File(conf.databasePath)
    if (databaseDir.isDirectory) {
      subLogger.out.println(s"Database found in ${databaseDir.getAbsolutePath}. If you move or delete that directory, Overview will create a new database next time you run it.")
    } else if (databaseDir.exists) {
      subLogger.err.println(s"File ${databaseDir.getAbsolutePath} is not a directory! Overview needs this directory to run. Please move ${databaseDir.getAbsolutePath} somewhere else so Overview can use that spot for its database.")
      System.exit(1)
    } else {
      subLogger.out.println(s"No database found at ${databaseDir.getAbsolutePath}. Invoking initdb.")

      val daemon = new Daemon(
        subLogger,
        PostgresCommand("initdb", "-D", databaseDir.getAbsolutePath, "-E", "UTF8", "--no-locale", "-U", "postgres")
      )
      val statusCode = Await.result(daemon.waitFor, Duration.Inf)

      if (statusCode != 0) {
        subLogger.err.println(s"initdb failed with status code ${statusCode}. Please delete ${databaseDir.getAbsolutePath} and try again.")
        System.exit(1)
      }
    }

    subLogger.out.println("Refreshing postgresql.conf")
    copyPostgresqlConf(databaseDir)

    withPostgresServerRunning(subLogger) { conn =>
      using(conn.createStatement()) { st =>
        // create "overview" user
        using(st.executeQuery("SELECT 1 FROM pg_user WHERE usename = 'overview'")) { rs =>
          if (!rs.isBeforeFirst()) {
            using(conn.createStatement()) { st2 =>
              st.executeUpdate("CREATE ROLE overview LOGIN PASSWORD 'overview'")
            }
          }
        }

        // create "overview-dev" database
        using(st.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'overview-dev'")) { rs =>
          if (!rs.isBeforeFirst()) {
            using(conn.createStatement()) { st2 =>
              st.executeUpdate("CREATE DATABASE \"overview-dev\" OWNER overview")
            }
          }
        }

        // create "overview-test" database
        using(st.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'overview-test'")) { rs =>
          if (!rs.isBeforeFirst()) {
            using(conn.createStatement()) { st2 =>
              st.executeUpdate("CREATE DATABASE \"overview-test\" OWNER overview")
            }
          }
        }
      }
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
    def makeDaemon(spec: DaemonInfo, classpath: Seq[String]) : Daemon = {
      val command = spec.command match {
        case c: JvmCommandWithAppendableClasspath => c.withClasspath(classpath).with32BitSafe
        case c: JvmCommand => c.with32BitSafe
        case c: Command => c
      }
      val sublogger = if (spec.id == "database") {
        logger.sublogger(spec.id, Some(spec.colorCode.getBytes())).treatingErrorsAsInfo
      } else {
        logger.sublogger(spec.id, Some(spec.colorCode.getBytes()))
      }
      new Daemon(sublogger, command)
    }

    logger.out.println(s"Preparing to start ${daemonInfos.map(_.id).mkString(", ")}")

    if (daemonInfos.exists(_.id == "database")) {
      ensureDatabaseClusterExists()
    }

    // Start all the daemons at once.
    //
    // (There's no good reason not to start them simultaneously. Our system
    // needs to recover gracefully in each component.)
    //
    // Note that we asynchronously A) start the database server and B) run
    // evolutions on it. This is fine: starting the database is much faster
    // than running sbt (sbt runs the db-evolution-applier). And even if it
    // weren't fast enough, db-evolution-applier will retry the database a
    // few times to make sure.
    val (jvmDaemons, rawDaemons) = daemonInfos.partition {
      (info: DaemonInfo) => !Set("database", "sbt-task").contains(info.id)
    }
    val daemons =
      rawDaemons.map(makeDaemon(_, Seq())) ++ // start Postgres before getClasspaths...
      (jvmDaemons.zip(getClasspathsAndRunEvolutionsAsASideEffect(jvmDaemons.map(_.id)))
        .map { case (info, classpath) => makeDaemon(info, classpath) })

    val statusCode = waitForDaemons(daemons)

    logger.out.println(s"All processes exited. (First exit status code was ${statusCode}. Shutting down.")
    System.exit(statusCode)
  }
}

object Main {
  def main(args: Array[String]) : Unit = {
    val conf = new Conf(DaemonInfoRepository, args)
    new Main(conf).run()
  }
}
