/*
 * DatabaseConfiguration.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */

package org.overviewproject.database

import com.typesafe.config.ConfigFactory

trait DatabaseConfiguration {
  val databaseDriver : String
  val databaseUrl: String
  val username: String
  val password: String
}

object DatabaseConfiguration {
  private class UrlBasedDatabaseConfiguration(private val givenUrl: String) extends DatabaseConfiguration {
    override val databaseDriver = "org.postgresql.Driver"
    override val (databaseUrl, username, password) = {
      val urlPattern = """[^:]+://([^:]+):([^@]+)@([^/]+)/(.+)""".r

      val urlPattern(user, password, host, database) = givenUrl

      ("jdbc:postgresql://" + host + "/" + database, user, password)
    }
  }

  /**
   * Sets up properties needed to configure the database.
   * Reads url in Play's format http://user:password@host/database
   * and converts to jdbc:postgresql://host/database, with user and password in
   * separate configuration variables.
   */
  def fromUrl(url: String): DatabaseConfiguration = new UrlBasedDatabaseConfiguration(url)

  def fromSystemProperty(property: String): DatabaseConfiguration = {
    val url = sys.props.get(property)
      .getOrElse(throw new Error(s"You must run Java with -D${property}=something"))
    fromUrl(url)
  }

  /** Reads from datasource.default.url or db.default.url system property. */
  lazy val fromSystemProperties: DatabaseConfiguration = fromSystemProperty("datasource.default.url")

  /** Reads db.default.url from application.conf. */
  lazy val fromConfig: DatabaseConfiguration = {
    val config = ConfigFactory.load
    val url = config.getString("db.default.url")
    fromUrl(url)
  }
}
