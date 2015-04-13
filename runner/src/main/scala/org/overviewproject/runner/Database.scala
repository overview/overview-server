package org.overviewproject.runner

import java.io.{File,FileWriter,InputStream}
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files,Paths}
import java.sql.{Connection,DriverManager,PreparedStatement}
import resource.managed
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.Source

import org.overviewproject.runner.commands.{Command,PostgresCommand}

trait DatabaseLike {
  val file: File
  val postgresqlConfContents: Seq[(String,String)]
  val logger : StdLogger

  protected val postgresCommands : DatabaseLike.PostgresCommands
  protected val postgresInstructions : DatabaseLike.PostgresInstructions
  protected val runner : DatabaseLike.Runner
  protected val filesystem: DatabaseLike.Filesystem

  /** Ensures the databases/users exist, spins everything up, and runs the given block.
    *
    * On a user-presentable error, returns a Left[String]. On a coding error,
    * throws an Exception. On success, returns a Right[A].
    */
  def withDatabase[A](block: () => A) : Either[String,A] = {
    ensureCluster.right.map { Unit =>
      rewriteConfig

      runner.running(postgresCommands.server) { () =>
        managed(postgresInstructions.connect(100, 100)).acquireAndGet { connection =>
          ensureUser(connection)
          ensureDatabases(connection)
        }
        block()
      }
    }
  }

  private[runner] def ensureCluster : Either[String,Unit] = {
    def success(message: String) : Right[String,Unit] = {
      logger.out.println(message)
      Right(())
    }

    def error(message: String) : Left[String,Unit] = {
      logger.err.println(message)
      Left(message)
    }

    if (file.isDirectory) {
      success(s"Database found in ${file.getAbsolutePath}. If you move or delete that directory, Overview will create a new database next time you run it.")
    } else if (file.exists) {
      error(s"File ${file.getAbsolutePath} is not a directory! Overview needs this directory to run. Please move ${file.getAbsolutePath} somewhere else so Overview can use that spot for its database.")
    } else {
      val statusCode = runner.run(postgresCommands.initdb)

      if (statusCode != 0) {
        error(s"initdb failed with status code ${statusCode}. Please delete ${file.getAbsolutePath} and try again.")
      } else {
        success(s"created database in ${file.getAbsolutePath}. Protect this directory! It contains your personal data.")
      }
    }
  }

  private[runner] def rewriteConfig: Unit = {
    def writeFile(filename: String, contents: String): Unit = {
      val path = new File(file.getAbsolutePath(), filename).getAbsolutePath()
      filesystem.createFileWithContents(path, contents)
      logger.out.println(s"Wrote ${path}")
    }

    postgresqlConfContents.foreach((writeFile _).tupled)
  }

  private[runner] def ensureUser(connection: Connection): Unit = {
    postgresInstructions.createUserIfNotExists(connection, "overview", "overview")
  }

  private[runner] def ensureDatabases(connection: Connection): Unit = {
    postgresInstructions.createDatabaseIfNotExists(connection, "overview", "overview")
    postgresInstructions.createDatabaseIfNotExists(connection, "overview-dev", "overview")
    postgresInstructions.createDatabaseIfNotExists(connection, "overview-test", "overview")
  }
}

object DatabaseLike {
  trait PostgresCommands {
    /** Initializes a Postgres database.
      *
      * When this command exits with status 0, there is a database. When it
      * exits with some other status, there is not.
      */
    val initdb : Command

    /** Runs a Postgres server.
      *
      * After this process starts and before it finishes, clients can connect
      * to it. Beware the obvious race condition: clients can't connect
      * instantaneously after this command has started. (Real-world, it usually
      * takes about a second to connect; it can take lots longer if Postgres
      * needs to repair the database.)
      */
    val server : Command
  }

  trait Runner {
    /** Starts command; runs block(); kills command; waits for process exit; returns block()'s retval. */
    def running[A](command: Command)(block: () => A) : A

    /** Runs a command; returns exit code. */
    def run(command: Command) : Int
  }

  trait PostgresInstructions {
    /** Creates user with given password, if the user does not exist.
      *
      * On error, throws an exception.
      */
    def createUserIfNotExists(connection: Connection, username: String, password: String) : Unit

    /** Creates database with given name and owner, if it does not exist.
      *
      * On error, throws an exception.
      */
    def createDatabaseIfNotExists(connection: Connection, database: String, owner: String) : Unit

    /** Returns a Connection.
      *
      * On error, throws an exception.
      */
    def connect(retries: Int, retryWaitMilliseconds: Int): Connection
  }

  trait Filesystem {
    def createFileWithContents(file: String, contents: String) : Unit
  }
}

class Database(val file: File, val postgresqlConf: Seq[(String,InputStream)], val logger: StdLogger) extends DatabaseLike {
  private def readResource(name: String, inputStream: InputStream): (String,String) = {
    (name -> Source.fromInputStream(inputStream).getLines.mkString("\n"))
  }

  override val postgresqlConfContents = postgresqlConf.map((readResource _).tupled)

  override protected val postgresCommands = new DatabaseLike.PostgresCommands {
    val initdb = PostgresCommand("initdb", "-D", file.getAbsolutePath, "-E", "UTF8", "--no-locale", "-U", "postgres")
    val server = PostgresCommand("postgres", "-D", file.getAbsolutePath, "-k", file.getAbsolutePath)
  }

  override protected val runner = new DatabaseLike.Runner {
    override def running[A](command: Command)(block: () => A) = {
      val daemon = new Daemon(logger, command)
      try {
        block()
      } finally {
        daemon.destroy()
        Await.result(daemon.waitFor, Duration.Inf)
      }
    }

    override def run(command: Command) : Int = {
      val daemon = new Daemon(logger, command)
      Await.result(daemon.waitFor, Duration.Inf)
    }
  }

  override protected val postgresInstructions = new DatabaseLike.PostgresInstructions {
    class Helper(connection: Connection) {
      private def prepareStatement(query: String, params: Seq[String]) : PreparedStatement = {
        val statement = connection.prepareStatement(query)
        params.zipWithIndex.foreach({ case (s: String, i: Int) => statement.setString(i + 1, s) })
        statement
      }

      /** Queries query1 with params1; if there are no rows, executes query2 with params2 */
      def run2unless1(query1: String, params1: Seq[String], query2: String, params2: Seq[String]) : Unit = {
        for (st1 <- managed(prepareStatement(query1, params1));
             rs1 <- managed(st1.executeQuery())) {

          if (!rs1.next()) {
            for (st2 <- managed(prepareStatement(query2, params2))) {
              st2.executeUpdate()
            }
          }
        }
      }
    }

    override def createUserIfNotExists(connection: Connection, username: String, password: String) = {
      new Helper(connection).run2unless1(
        "SELECT 1 FROM pg_user WHERE usename = ?", Seq(username),
        s"""CREATE ROLE "${username}" LOGIN PASSWORD '${password}'""", Seq()
      )
    }

    override def createDatabaseIfNotExists(connection: Connection, database: String, owner: String) = {
      new Helper(connection).run2unless1(
        "SELECT 1 FROM pg_database WHERE datname = ?", Seq(database),
        s"""CREATE DATABASE "${database}" OWNER "${owner}"""", Seq()
      )
    }

    @tailrec
    override def connect(retries: Int, retryWaitMilliseconds: Int) : Connection = {
      try {
        DriverManager.getConnection("jdbc:postgresql://localhost:9010/postgres?user=postgres&ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory")
      } catch { case e: Exception =>
        if (retries <= 1) throw e

        Thread.sleep(retryWaitMilliseconds)
        connect(retries - 1, retryWaitMilliseconds)
      }
    }
  }

  override protected val filesystem = new DatabaseLike.Filesystem {
    override def createFileWithContents(file: String, contents: String) = {
      for (out <- managed(new FileWriter(file))) {
        out.write(contents)
        // server.key needs to be private, or Postgres won't start
        Files.setPosixFilePermissions(Paths.get(file), PosixFilePermissions.fromString("rwx------"))
      }
    }
  }
}
