package org.overviewproject.db_evolution_applier

import com.typesafe.config.ConfigFactory
import java.io.File
import play.api.{Configuration,DefaultApplication,Mode,Plugin,Play}

/** Simple program to run Play evolutions.
  *
  * This can't be a simple sbt task, since it has Play's dependencies.
  *
  * When this task ends with status code 0, that means the database evolutions
  * have all been applied.
  */
object Main {
  def main(args: Array[String]) : Unit = {
    val config = ConfigFactory.parseString("""
      |applyEvolutions.default=true
      |applyDownEvolutions.default=true
      |db {
      |  default {
      |    dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
      |    maximumPoolSize=2
      |    dataSource {
      |      serverName="localhost"
      |      portNumber="9010" # overridden in production.conf
      |      databaseName="overview-dev"
      |      user="overview"
      |      password="overview"
      |      serverName=${?DATABASE_SERVER_NAME}
      |      portNumber=${?DATABASE_PORT}
      |      databaseName=${?DATABASE_NAME}
      |      user=${?DATABASE_USERNAME}
      |      password=${?DATABASE_PASSWORD}
      |      tcpKeepAlive=true
      |      ssl=${?DATABASE_SSL}
      |      sslfactory=${?DATABASE_SSL_FACTORY}
      |    }
      |  }
      |}
      |""".stripMargin).resolve()

    val application = new DefaultApplication(
      new java.io.File("."),
      getClass.getClassLoader,
      None,
      Mode.Prod
    ) {
      override lazy val configuration = Configuration(config)

      override lazy val plugins : Seq[play.api.Plugin] = Seq(
        new com.edulify.play.hikaricp.HikariCPPlugin(this),
        new play.api.db.evolutions.EvolutionsPlugin(this)
      )
    }

    Play.start(application)
    Play.stop()
  }
}
