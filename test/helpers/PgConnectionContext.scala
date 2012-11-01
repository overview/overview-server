package helpers

import com.jolbox.bonecp.ConnectionHandle
import models.orm.SquerylPostgreSqlAdapter
import org.postgresql.PGConnection
import org.specs2.execute.Result
import org.specs2.mutable.Around
import org.squeryl.Session
import org.squeryl.PrimitiveTypeMode.using
import play.api.db.DB
import play.api.Play.current

// We can't use DbTestContext, because it uses a connection
// returned from DB.withConnection, which is an AutoCleanConnection wrapper
// around bonecp.ConnectionHandle
trait PgConnectionContext extends Around {
  implicit var pgConnection: PGConnection = _

  def setupWithDb = {}

  def around[T <% Result](test: => T) = {
    // DB.getConnection returns an unwrapped bonecp.ConnectionHandle
    // hopefully we are not leaking statements, but as long as we
    // just use the LargeObjectAPI, we should be ok...
    val connection = DB.getConnection(autocommit = false)
    try {
      val adapter = new SquerylPostgreSqlAdapter()
      val session = new Session(connection, adapter)

      val connectionHandle = connection.asInstanceOf[ConnectionHandle]
      pgConnection = connectionHandle.getInternalConnection.asInstanceOf[PGConnection]

      using(session) {
	setupWithDb
	test
      }
    } finally {
      connection.rollback()
      connection.close()
    }
  }
}
