/*
 * DB.scala
 *
 * Overview
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.database

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

  /**
   * Provides a scope with an implicit connection that is automatically closed.
   */
  def withConnection[T](block: Connection => T): T = {
    val connection = dataSource.getConnection
    try {
      block(connection)
    } finally {
      connection.close()
    }
  }

  /**
   * Provides a scope inside a transaction with an implicit connection .
   * If an error occurs, a rollback is performed. The connection is automatically closed.
   */
  def withTransaction[T](block: Connection => T): T = {
    withConnection { implicit connection =>
      try {
        connection.setAutoCommit(false)
        val result = block(connection)
        connection.commit()

        result
      } catch {
        case e : Throwable => {
          connection.rollback()
          throw e
        }
      } finally {
        connection.setAutoCommit(true)
      }
    }
  }

  /** Extracts the internal PGConnection. */
  def pgConnection(implicit connection: Connection): PGConnection = connection.unwrap(classOf[PGConnection])
}

