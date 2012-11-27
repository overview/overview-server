/*
 * DataSource.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package overview.database


import java.sql.Connection
import com.jolbox.bonecp._

/**
 * Wrapper for BoneCPDataSource, that applies the given configuration.
 * Should be shutdown() when program ends to shutdown BoneCP connection pool.
 */
class DataSource(configuration: DatabaseConfiguration) {

  Class.forName(configuration.databaseDriver)

  private val dataSource = new BoneCPDataSource()

  dataSource.setJdbcUrl(configuration.databaseUrl)
  dataSource.setUsername(configuration.username)
  dataSource.setPassword(configuration.password)
  dataSource.setMinConnectionsPerPartition(1)
  dataSource.setMaxConnectionsPerPartition(5)
  dataSource.setAcquireIncrement(1)
  dataSource.setPartitionCount(1)
  dataSource.setDisableJMX(true)
  dataSource.setLogStatementsEnabled(true)

  def getConnection(): Connection = {
    dataSource.getConnection()
  }

  def shutdown() {
    dataSource.close()
  }
}
