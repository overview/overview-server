package helpers

import org.specs2.mutable.Around
import org.specs2.execute.Result

import org.squeryl.PrimitiveTypeMode.inTransaction
import org.squeryl.Session

import play.api.test._
import play.api.test.Helpers._


trait  DbTestContext extends Around {
  implicit lazy val connection = Session.currentSession.connection
      
  def around[T <% Result](test: => T) = {
    running(FakeApplication()) {
      inTransaction {
        val result = test
        connection.rollback()
            
        result
      }
    }
  }
   
}