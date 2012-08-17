/*
 * DbSpecification.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package helpers

import org.specs2.mutable.Specification
import database.{DataSource, DatabaseConfiguration, DB}
/**
 * Tests that access the database should extend DbSpecification.
 * Before any examples, call step(setupDB), and after examples call step(shutdownDB).
 */
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