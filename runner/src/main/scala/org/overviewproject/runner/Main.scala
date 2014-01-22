package org.overviewproject.runner

import java.io.{ByteArrayOutputStream,File,FileOutputStream}
import java.sql.{Connection,DriverManager,ResultSet}
import java.util.concurrent.TimeoutException
import scala.concurrent.Await
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
      daemon.destroyAsynchronously()
      val statusCode = Await.result(daemon.statusCodeFuture, Duration.Inf)
    }
  }

  lazy val logger = new Logger(System.out, System.err)

  val daemonInfos = conf.daemonInfos

  /** Returns classpaths, one per daemonInfo, in the same order as daemonInfos.
   */
  def getClasspaths(daemonIds: Seq[String]) : Seq[Seq[String]] = {
    if (daemonIds.isEmpty) {
      Seq()
    } else {
      logger.out.println("Compiling and fetching...")

      val cpStream = new ByteArrayOutputStream()
      val cpLogger = new Logger(new BiOutputStream(System.out, cpStream), System.err)
      val sublogger = cpLogger.sublogger("sbt", Some(Console.BLUE.getBytes()))

      val sbtTasks = daemonIds.map { s: String => s"show ${s}/full-classpath" }
      val sbtCommand = (Seq("", "all/compile") ++ sbtTasks).mkString("; ")

      val sbtRun = new Daemon(sublogger, commands.sbt(sbtCommand))
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
      val statusCode = Await.result(daemon.statusCodeFuture, Duration.Inf)

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

  def run() = {
    def makeDaemon(spec: DaemonInfo, classpath: Seq[String]) : Daemon = {
      val command = spec.command match {
        case c: JvmCommandWithAppendableClasspath => c.withClasspath(classpath)
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

    // Start all the daemons
    val (jvmDaemons, rawDaemons) = daemonInfos.partition(_.command.isInstanceOf[JvmCommand])

    val daemons =
      jvmDaemons.zip(getClasspaths(jvmDaemons.map(_.id))).map { case (info, classpath) => makeDaemon(info, classpath) } ++
      rawDaemons.map(makeDaemon(_, Seq()))

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

        if (shuttingDown) daemon.destroyAsynchronously()

        // Is the daemon dead? If so, break so we get to the next daemon
        try {
          val duration = Duration(100, "ms")
          val statusCode = Await.result(daemon.statusCodeFuture, duration)
          daemon.logger.out.println(s"Process exited with status code ${statusCode}\n")
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
