/*
 * DB.scala
 *
 * Overview
 * Created by Jonas Karlsson, Aug 2012
 */

package com.overviewdocs.database

import com.zaxxer.hikari.{HikariConfig,HikariDataSource}
import java.sql.Connection
import org.postgresql.PGConnection

/**
 * Convenience object for database access.
 *
 * Reads Hikari connection configuration from Typesafe Config, in `db.default`.
 */
object DB {
  lazy val dataSource: HikariDataSource = {
    val hikariConfig = DatabaseConfiguration.fromConfig
    new HikariDataSource(hikariConfig)
  }

  /**
   * @return a connection. Caller is responsible for closing connection.
   */
  def getConnection(): Connection = {
    dataSource.getConnection
  }
}

