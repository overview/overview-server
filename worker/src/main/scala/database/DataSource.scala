package database

import com.jolbox.bonecp._
import java.sql.Connection

class DataSource(configuration: DatabaseConfiguration) {

  Class.forName(configuration.databaseDriver)

  private val dataSource = new BoneCPDataSource()

  dataSource.setJdbcUrl(configuration.databaseUrl)
  dataSource.setUsername(configuration.username)
  dataSource.setPassword(configuration.password)
  dataSource.setMinConnectionsPerPartition(5)
  dataSource.setMaxConnectionsPerPartition(30)
  dataSource.setPartitionCount(1)
  dataSource.setDisableJMX(true)
  dataSource.setLogStatementsEnabled(true)
    
  def getConnection() : Connection = {
    dataSource.getConnection()
  }
    
  def shutdown()  {
    dataSource.close()
  }
}