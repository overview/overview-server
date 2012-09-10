/*
 * DbSpecification.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package helpers

import org.specs2.execute.Result
import org.specs2.mutable.Around
import org.specs2.mutable.Specification
import database.{DataSource, DatabaseConfiguration, DB}

/**
 * Tests that access the database should extend DbSpecification.
 * Before any examples, call step(setupDB), and after examples call step(shutdownDB).
 */
class DbSpecification extends Specification {

/**
 * Context for test accessing the database. All tests are run inside a transaction
 * which is rolled back after the test is complete.
 */
  trait DbTestContext extends Around {
    lazy implicit val connection = DB.getConnection()

    def around[T <% Result](test: => T) = {
      try {
        connection.setAutoCommit(false)
        test
      } finally {
        connection.rollback()
        connection.close()
      }
    }
  }

  
  def setupDb() {
    val dataSource = new DataSource(new DatabaseConfiguration)
    DB.connect(dataSource)  
  }

  def shutdownDb() {
    DB.close()
  }
  
}
