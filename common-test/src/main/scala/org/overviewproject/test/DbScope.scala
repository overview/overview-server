package org.overviewproject.test


import java.sql.Connection
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration
import scala.slick.jdbc.JdbcBackend.Session
import scala.slick.jdbc.UnmanagedSession
import org.overviewproject.database.DB
import org.overviewproject.test.factories.{Factory,DbFactory}
import org.specs2.mutable.BeforeAfter

trait DbScope extends BeforeAfter {
  var connected = false
  lazy val connection: Connection = {
    connected = true
    val ret = DB.getConnection()
    ret.setAutoCommit(false)
    ret
  }
  lazy val session: Session = new UnmanagedSession(connection)
  lazy val factory: Factory = new DbFactory(connection)

  def await[A](f: Future[A]) = Await.result(f, Duration(2, "seconds"))

  override def before = ()

  override def after = {
    if (connected) {
      connection.rollback()
      connection.close()
    }
  }

  def sql(q: String): Unit = session.withPreparedStatement(q) { (st) => st.execute }
}


