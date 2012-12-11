/*
 * DbTestContext.scala 
 * 
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package helpers

import org.specs2.execute.Result
import org.specs2.mutable.Around
import org.squeryl.Session
import org.squeryl.PrimitiveTypeMode.using
import play.api.db.DB
import play.api.Play.current
import play.api.test._
import play.api.test.Helpers._

import models.OverviewDatabase

/**
 * A helper class for tests that access the test-database. Wraps the test in a
 * transaction.
 * The database connection is available as an implicit parameter. 
 */
trait DbTestContext extends Around {
  lazy implicit val connection = Session.currentSession.connection

  def setupWithDb = {}
  
  def around[T <% Result](test: => T) = {
    OverviewDatabase.inTransaction {
      try {
        setupWithDb
        test
      } finally {
        connection.rollback()
      }
    }
  }
}
