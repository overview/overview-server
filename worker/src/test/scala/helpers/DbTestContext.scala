/*
 * DbTestContext.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package helpers

import org.specs2.mutable.Around
import org.specs2.execute.Result
import org.squeryl.adapters.PostgreSqlAdapter
import database.DB


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
    }
    finally {
      connection.rollback()
      connection.close()
    }
  }
  
}
    
