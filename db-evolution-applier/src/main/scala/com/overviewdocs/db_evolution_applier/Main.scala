package com.overviewdocs.db_evolution_applier

import com.typesafe.config.{Config,ConfigFactory}
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import scala.util.Try

/** Simple program to run Play evolutions.
  *
  * This can't be a simple sbt task, since it has Play's dependencies.
  *
  * When this task ends with status code 0, that means the database evolutions
  * have all been applied.
  */
object Main {
  private lazy val dataSource: DataSource = {
    val entireConfig: Config = ConfigFactory.systemProperties
      .withFallback(ConfigFactory.parseString("""
        |# We keep 'db.default' so caller can override via Java system properties
        |db {
        |  default {
        |    properties {
        |      serverName="localhost"
        |      portNumber="5432"
        |      databaseName="overview"
        |      user="overview"
        |      password="overview"
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
    migrateToFlyway
    migrate
  }

  /** Convert a Play Evolutions-style schema to a Flyway-style one.
    *
    * Steps:
    *
    * 1. Check if there's a play_evolutions table.
    * 2. If there is, use the final applied ID as the Flyway baseline version.
    * 3. Delete the play_evolutions table.
    */
  private def migrateToFlyway: Unit = {
    val connection = dataSource.getConnection

    val maybePlayEvolutionVersion: Try[Int] = Try {
      val statement = connection.createStatement
      try {
        // Fail if the play_evolutions table doesn't exist ... which is okay;
        // it just means we ran migrateToFlyway before.
        val resultSet = statement.executeQuery("SELECT MAX(id) AS v FROM play_evolutions WHERE state = 'applied'")
        resultSet.next
        resultSet.getInt("v")
      } finally {
        statement.close
      }
    }

    maybePlayEvolutionVersion.foreach { playEvolutionVersion =>
      System.out.println(s"Migrating database from Play Evolutions (v$playEvolutionVersion) to Flyway...")
      val flyway = new Flyway
      flyway.setDataSource(dataSource)
      flyway.setLocations("migration")
      flyway.setBaselineVersionAsString(playEvolutionVersion.toString)
      flyway.baseline

      val dropStatement = connection.createStatement
      dropStatement.execute("DROP TABLE play_evolutions")
      dropStatement.close
    }

    connection.close
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
