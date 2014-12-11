package org.overviewproject.test

import java.sql.Connection
import org.specs2.execute.AsResult
import org.specs2.mutable.BeforeAfter
import org.specs2.specification.{Fragments, Step}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await,Future}
import scala.slick.jdbc.JdbcBackend.Session
import scala.slick.jdbc.UnmanagedSession

import org.overviewproject.database.DB



/**
 * Superclass for Specification that need an established Slick session
 */

class SlickSpecification extends DbSpecification {
  sequential

  override def map(fs: => Fragments) = {
    Step(setupDb) ^ super.map(fs) ^ Step(shutdownDb)
  }


  trait DbScope extends BeforeAfter {
    var connected = false
    lazy val connection: Connection = {
      connected = true
      val ret = DB.getConnection()
      ret.setAutoCommit(false)
      ret
    }
    
    implicit lazy val session: Session = new UnmanagedSession(connection)

    override def before = ()

    override def after = {
      if (connected) {
        connection.rollback()
        connection.close()
      }
    }

    def sql(q: String): Unit = session.withPreparedStatement(q) { (st) => st.execute }
  }

}