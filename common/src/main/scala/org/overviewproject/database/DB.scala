/*
 * DB.scala
 *
 * Overview
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.database

import java.sql.Connection
import javax.sql.{ DataSource => JDataSource }
import org.postgresql.PGConnection
import slick.jdbc.JdbcBackend.Session
import slick.jdbc.UnmanagedSession

/**
 * Convenience object for database access. Call DB.connect(datasSource) once at the start
 * of the application, and DB.close() at the end.
 */
object DB {
  var connected: Boolean = false
  private var dataSource: DataSource = _

  def connect(source: DataSource): Unit = {
    if (connected) throw new RuntimeException("You tried to connect to the database twice")
    dataSource = source
    connected = true
  }

  def close(): Unit = {
    dataSource.shutdown
    connected = false
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

  /** Transforms a JDBC Connection into a Slick Session. */
  def slickSession(connection: Connection): Session = new UnmanagedSession(connection)

  /**
   * @return the current underlying data source
   */
  def getDataSource(): JDataSource = dataSource.getDataSource
}

