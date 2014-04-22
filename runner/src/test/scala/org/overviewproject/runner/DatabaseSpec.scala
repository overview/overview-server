package org.overviewproject.runner

import scala.language.reflectiveCalls

import java.io.{File,PrintStream}
import java.sql.Connection
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import org.overviewproject.runner.commands.Command

class DatabaseSpec extends Specification with Mockito {
  trait BaseScope extends Scope {
    val mockPostgresCommands = new DatabaseLike.PostgresCommands {
      val initdb = mock[Command].smart
      val server = mock[Command].smart
    }
    val mockPostgresInstructions = mock[DatabaseLike.PostgresInstructions]
    mockPostgresInstructions.connect(anyInt, anyInt) returns mock[Connection]
    val mockRunner = mock[DatabaseLike.Runner].smart
    val mockFilesystem = mock[DatabaseLike.Filesystem]
    val mockLogger = new StdLogger {
      val out = mock[PrintStream]
      val err = mock[PrintStream]
    }
    val mockFile = mock[File].smart
    mockFile.getAbsolutePath() returns "/foo"

    lazy val database = new DatabaseLike {
      override val file = mockFile
      override val logger = mockLogger
      override val postgresqlConfContents = "# Config file contents"
      override protected val postgresCommands = mockPostgresCommands
      override protected val postgresInstructions = mockPostgresInstructions
      override protected val runner = mockRunner
      override protected val filesystem = mockFilesystem
    }
  }

  "Database" should {
    "when the database is a directory already" should {
      trait DatabaseIsDirectoryScope extends BaseScope {
        mockFile.isDirectory returns true
        mockFile.exists returns true
      }

      "rewrite the config" in new DatabaseIsDirectoryScope {
        database.rewriteConfig
        there was one(mockFilesystem).createFileWithContents("/foo/postgresql.conf", "# Config file contents")
      }

      "ensureUser" should {
        "create the user" in new DatabaseIsDirectoryScope {
          database.ensureUser(mock[Connection])
          there was one(mockPostgresInstructions).createUserIfNotExists(any, anyString, anyString)
        }
      }

      "ensureDatabases" should {
        "create the databases" in new DatabaseIsDirectoryScope {
          database.ensureDatabases(mock[Connection])
          there were three(mockPostgresInstructions).createDatabaseIfNotExists(any, anyString, anyString)
        }
      }

      "ensureCluster" should {
        "run nothing" in new DatabaseIsDirectoryScope {
          database.ensureCluster
          there was no(mockRunner).run(any[Command])
        }

        "log success" in new DatabaseIsDirectoryScope {
          database.ensureCluster
          there was one(mockLogger.out).println(anyString)
        }

        "return a Right" in new DatabaseIsDirectoryScope {
          database.ensureCluster must beRight
        }
      }
    }

    "when the database is a file" should {
      trait DatabaseIsFileScope extends BaseScope {
        mockFile.isDirectory returns false
        mockFile.exists returns true
      }

      "ensureCluster" should {
        "run nothing" in new DatabaseIsFileScope {
          database.ensureCluster
          there was no(mockRunner).run(any[Command])
        }

        "log failure" in new DatabaseIsFileScope {
          database.ensureCluster
          there was one(mockLogger.err).println(anyString)
        }

        "return a Left" in new DatabaseIsFileScope {
          database.ensureCluster must beLeft
        }
      }
    }

    "when database is not present" should {
      trait DatabaseIsMissingScope extends BaseScope {
        mockFile.isDirectory returns false
        mockFile.exists returns false
      }

      "ensureCluster" should {
        "run initdb" in new DatabaseIsMissingScope {
          database.ensureCluster
          there was one(mockRunner).run(mockPostgresCommands.initdb)
        }

        "when initdb succeeds" should {
          trait DatabaseCreatedScope extends DatabaseIsMissingScope {
            mockRunner.run(mockPostgresCommands.initdb) returns 0
          }

          "logs success" in new DatabaseCreatedScope {
            database.ensureCluster
            there was one(mockLogger.out).println(anyString)
          }

          "returns a Right" in new DatabaseCreatedScope {
            database.ensureCluster must beRight
          }
        }

        "when initdb fails" should {
          trait InitdbFailedScope extends DatabaseIsMissingScope {
            mockRunner.run(mockPostgresCommands.initdb) returns 1
          }

          "logs success" in new InitdbFailedScope {
            database.ensureCluster
            there was one(mockLogger.err).println(anyString)
          }

          "returns a Right" in new InitdbFailedScope {
            database.ensureCluster must beLeft
          }
        }
      }
    }
  }
}
