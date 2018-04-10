package com.overviewdocs.db_evolution_applier

import com.typesafe.config.{Config,ConfigFactory}
import java.sql.{Connection,SQLException}
import java.util.logging.{Logger,Level}
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

/** Simple program to run Play evolutions.
  *
  * This can't be a simple sbt task, since it has Play's dependencies.
  *
  * When this task ends with status code 0, that means the database evolutions
  * have all been applied.
  */
object Main {
  private lazy val dataSource: PGSimpleDataSource = {
    val entireConfig: Config = ConfigFactory.systemProperties
      .withFallback(ConfigFactory.parseString("""
        |# We keep 'db.default' so caller can override via Java system properties
        |db {
        |  default {
        |    properties {
        |      serverName="database"
        |      portNumber="5432"
        |      databaseName="overview"
        |      user="overview"
        |      password=""
        |      serverName=${?DATABASE_SERVER_NAME}
        |      portNumber=${?DATABASE_PORT}
        |      databaseName=${?DATABASE_NAME}
        |      user=${?DATABASE_USERNAME}
        |      password=${?DATABASE_PASSWORD}
        |      ssl=${?DATABASE_SSL}
        |      sslfactory=${?DATABASE_SSL_FACTORY}
        |    }
        |  }
        |}
        |""".stripMargin)).resolve()
    val config = entireConfig.getConfig("db.default.properties")

    val ret = new PGSimpleDataSource
    ret.setServerName(config.getString("serverName"))
    ret.setPortNumber(config.getInt("portNumber"))
    ret.setDatabaseName(config.getString("databaseName"))
    if (config.hasPath("ssl")) {
      ret.setSsl(config.getBoolean("ssl"))
      ret.setSslfactory(config.getString("sslfactory"))
    }
    ret.setUser(config.getString("user"))
    ret.setPassword(config.getString("password"))

    ret
  }

  def main(args: Array[String]) : Unit = {
    connect.close // Wait for database to spin up
    migrate
  }

  /** Synchronously connect to the database. Try until it succeeds.
    */
  private def connect = {
    var ret: Option[Connection] = None

    // pgjdbc logs the exception with SEVERE. That seems stupid: if we don't
    // catch the exception, isn't it going to be printed anyway? We have to
    // disable logging, because we _expect_ to try and connect when the database
    // is down. Because it's 2017, and that's how we do things in 2017.
    val logger = Logger.getLogger("org.postgresql.Driver")

    while (ret.isEmpty) {
      val logLevel = logger.getLevel
      logger.setLevel(Level.OFF)
      try {
        ret = Some(dataSource.getConnection)
        System.out.println("Connected to " + dataSource.getUrl + " as " + dataSource.getUser)
      } catch {
        case e: SQLException => {
          System.err.println("Failed to connect to " + dataSource.getUrl + " as " + dataSource.getUser + ". Will retry in 1s.")
          Thread.sleep(1000)
        }
      } finally {
        logger.setLevel(logLevel)
      }
    }

    ret.get
  }

  /** Call Flyway's migration code.
    */
  private def migrate: Unit = {
    val flyway = new Flyway
    flyway.setDataSource(dataSource)
    flyway.setLocations("migration")
    flyway.setValidateOnMigrate(false)

    val nPendingMigrations = flyway.info.pending.length
    if (nPendingMigrations > 0) {
      System.out.println(s"Applying ${nPendingMigrations} database migrations...")
      flyway.migrate
    }
  }
}
