/*
 * DbTestContext.scala 
 * 
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package helpers

import anorm._
import org.specs2.execute.Result
import org.specs2.mutable.Around
import org.squeryl.PrimitiveTypeMode.inTransaction
import org.squeryl.Session

import play.api.test._
import play.api.test.Helpers._


 /**
  *  A helper class for tests that access the test-database. Wraps the test in a
  *  transaction.
  *  The database connection is available as an implicit parameter. 
  */
trait  DbTestContext extends Around {
  lazy implicit val connection = Session.currentSession.connection
  
  def around[T <% Result](test: => T) = {

    inTransaction {
	  val result = test
	  connection.rollback()
            
	  result
	}
  }
}