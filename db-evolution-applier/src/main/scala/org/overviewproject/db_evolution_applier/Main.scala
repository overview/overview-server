package org.overviewproject.db_evolution_applier

import com.typesafe.config.{Config,ConfigFactory}
import com.zaxxer.hikari.{HikariConfig,HikariDataSource}
import java.io.InputStream
import java.sql.Connection
import java.util.Properties
import play.api.db.{DBApi,Database}
import play.api.db.evolutions.{DatabaseEvolutions,DefaultEvolutionsApi,EvolutionsApi,ClassLoaderEvolutionsReader}
import scala.collection.JavaConversions.asScalaSet

/** Simple program to run Play evolutions.
  *
  * This can't be a simple sbt task, since it has Play's dependencies.
  *
  * When this task ends with status code 0, that means the database evolutions
  * have all been applied.
  */
object Main {
  def main(args: Array[String]) : Unit = {
    val DatabaseName: String = "default"

    val config: Config = ConfigFactory.systemProperties
      .withFallback(ConfigFactory.parseString("""
        |# We keep 'db.default' so caller can override via Java system properties
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
        |""".stripMargin)).resolve()

    val hikariConfig: HikariConfig = configToHikariConfig(config, DatabaseName)
    val hikariDataSource: HikariDataSource = new HikariDataSource(hikariConfig)
    val database: Database = new HikariDatabase(DatabaseName, hikariDataSource)
    val dbApi: DBApi = new SingleDatabaseDBApi(database)
    val evolutionsApi: EvolutionsApi = new DefaultEvolutionsApi(dbApi)

    val dbEvolutions = new DatabaseEvolutions(database)
    val evolutions = dbEvolutions.scripts(new HackyClassLoaderEvolutionsReader)

    if (evolutions.length > 0) {
      System.out.println(s"Applying ${evolutions.length} database evolutions...")
    } else {
      System.out.println("Database schema is fresh: no new evolutions to apply")
    }

    dbEvolutions.evolve(evolutions, true)
  }

  private def configToHikariConfig(rootConfig: Config, databaseName: String): HikariConfig = {
    val config = rootConfig.getConfig("db").getConfig(databaseName)
    val props = new Properties()
    val entrySet = asScalaSet(config.entrySet)
    entrySet.foreach { entry => props.setProperty(entry.getKey, entry.getValue.unwrapped.toString) }
    new HikariConfig(props)
  }
}

class HikariDatabase(override val name: String, override val dataSource: HikariDataSource) extends Database {
  override def url = "[you-do-not-need-this]"
  override def getConnection = getConnection(false)
  override def getConnection(autocommit: Boolean) = {
    val ret = dataSource.getConnection
    if (!autocommit) ret.setAutoCommit(false)
    ret
  }
  override def withConnection[A](autocommit: Boolean)(block: Connection => A) = {
    val connection = getConnection(autocommit)
    try {
      block(connection)
    } finally {
      connection.close
    }
  }
  override def withConnection[A](block: Connection => A) = withConnection(true)(block)
  override def withTransaction[A](block: Connection => A) = withConnection(false)(block)
  override def shutdown = dataSource.shutdown
}

class SingleDatabaseDBApi(database: Database) extends DBApi {
  override def databases = Seq(database)
  override def database(name: String) = database
  override def shutdown = database.shutdown
}

class HackyClassLoaderEvolutionsReader extends ClassLoaderEvolutionsReader {
  override def loadResource(db: String, revision: Int): Option[InputStream] = {
    val path = s"/${db}/${revision}.sql"
    Option(getClass.getResourceAsStream(path))
  }
}
