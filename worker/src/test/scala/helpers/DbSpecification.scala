package helpers

import org.specs2.mutable.Specification
import database.{DataSource, DatabaseConfiguration, DB}

class DbSpecification extends Specification {
  var dataSource: DataSource = _
  
  def setupDB() {
    dataSource = new DataSource(new DatabaseConfiguration)
    DB.connect(dataSource)  
  }

  def shutdownDB() {
    DB.close()
  }
  
}