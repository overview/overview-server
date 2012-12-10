package models

import java.sql.Connection
import org.squeryl.{PrimitiveTypeMode, Session}
import play.api.Play.current
import play.api.db.DB

import org.overviewproject.postgres.SquerylPostgreSqlAdapter

/** Manages database connections.
  *
  * Overview uses a quasi-global (thread-local) Squeryl Session, so that we
  * don't need to pass the variable around a lot.
  *
  * Always use an inTransaction call to access the database. Changes will be
  * saved to the database when the block returns successfully.
  */
object OverviewDatabase {
  /** Executes the block with a thread-local Session.
    *
    * If the block succeeds, this method will end with a database COMMIT.
    * Otherwise, it will ROLLBACK.
    *
    * This method may not be nested.
    */
  def inTransaction[A](block: () => A) = {
    if (isInTransaction) throw new Exception("inTransaction may not be nested")

    DB.withTransaction { implicit connection =>
      val adapter = new SquerylPostgreSqlAdapter()
      val session = new Session(connection, adapter)
      PrimitiveTypeMode.using(session) { // sets thread-local variable
        block()
      }
    }
  }

  /** Returns true when called within an inTransaction block.
    */
  def isInTransaction : Boolean = Session.hasCurrentSession

  /** Returns the thread-local Session, or throws an exception if there is none.
    *
    * A session is always set within an inTransaction block.
    */
  def currentSession : Session = Session.currentSession

  /** Returns the thread-local Connection, or throws an exception if there is none.
    *
    * A connection is always set within an inTransaction block.
    */
  def currentConnection : Connection = currentSession.connection
}
