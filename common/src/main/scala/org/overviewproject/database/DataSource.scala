/*
 * DataSource.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.database

import com.zaxxer.hikari.{HikariConfig,HikariDataSource}
import java.io.{ByteArrayOutputStream,PrintWriter}
import java.sql.Connection
import javax.sql.{ DataSource => JDataSource }

trait DataSource {
  def getConnection: Connection
  def shutdown: Unit
  def getDataSource: JDataSource
}

object DataSource {
  private val logger = org.overviewproject.util.Logger.forClass(getClass)
  private var lastHikari: Option[(HikariConfig,HikariDataSource)] = None

  /** A data source built around Hikari.
    *
    * We do not close this data source when the shutdown() method is called.
    * Instead, we make two assumptions:
    *
    * 1. We assume only one DataSource is active at any given time; and
    * 2. We predict that users will usually connect to the same data source
    *    time and again. (This happens in unit tests.)
    *
    * The caller is responsible for calling `dataSource.close()`. It uses
    * `lastHikari` to do that.
    */
  class HikariDataSourceAdapter(private val dataSource: HikariDataSource) extends DataSource {
    override def getConnection: Connection = dataSource.getConnection
    override def shutdown: Unit = ()
    override def getDataSource: JDataSource = dataSource
  }

  /** Adapter for an existing DataSource.
    *
    * This adapter does not implement shutdown(): we assume the caller takes
    * care of that.
    */
  def apply(dataSource: JDataSource) = new DataSource {
    override def getConnection: Connection = dataSource.getConnection()
    override def shutdown: Unit = {}
    override def getDataSource: JDataSource = dataSource
  }

  /**
   * Wrapper for HikariCPDataSource, that applies the given configuration.
   * Should be shutdown() when program ends to shutdown BoneCP connection pool.
   */
  def apply(hikariConfig: HikariConfig) = {
    val hikariDataSource = lastHikari
      .map { case (lastConfig, lastHikariDataSource) =>
        if (hikariConfigToComparable(lastConfig) == hikariConfigToComparable(hikariConfig)) {
          logger.debug("Reusing Hikari data source")
          lastHikariDataSource
        } else {
          lastHikariDataSource.close
          new HikariDataSource(hikariConfig)
        }
      }
      .getOrElse(new HikariDataSource(hikariConfig))

    lastHikari = Some((hikariConfig, hikariDataSource))

    new HikariDataSourceAdapter(hikariDataSource)
  }

  private def hikariConfigToComparable(hikariConfig: HikariConfig): String = {
    val props = hikariConfig.getDataSourceProperties
    val stream = new ByteArrayOutputStream
    val writer = new PrintWriter(stream)
    props.list(writer)
    writer.close
    val bytes = stream.toByteArray
    new String(bytes, "utf-8") // this is a bit optimistic; we can revisit if it crashes
  }
}
