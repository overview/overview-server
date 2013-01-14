package org.overviewproject.database

import java.sql.Connection
import org.squeryl.Session
import org.overviewproject.postgres.{ SquerylEntrypoint, SquerylPostgreSqlAdapter }

/**
 * Manages database connections.
 *
 * Overview uses a quasi-global (thread-local) Squeryl Session, so that we
 * don't need to pass the variable around a lot.
 *
 * Always use an inTransaction call to access the database. Changes will be
 * saved to the database when the block returns successfully.
 * 
 * Concrete implementations use the relevant database framework to setup a 
 * transaction using the transactionBlock method.
 */
abstract class TransactionProvider {
  /**
   * Executes the block with a thread-local Session.
   *
   * If the block succeeds, this method will end with a database COMMIT.
   * Otherwise, it will ROLLBACK.
   *
   * If this method is nested, the inner call will not COMMIT.
   */
  
  def inTransaction[A](block: => A): A = {
    if (isInTransaction) {
      block
    } else {
      transactionBlock { implicit connection =>
        val adapter = new SquerylPostgreSqlAdapter()
        val session = new Session(connection, adapter)
        SquerylEntrypoint.using(session) { // sets thread-local variable
          block
        }
      }
    }
  }

  /**
   * Returns true when called within an inTransaction block.
   */
  def isInTransaction: Boolean = Session.hasCurrentSession

  /**
   * Returns the thread-local Session, or throws an exception if there is none.
   *
   * A session is always set within an inTransaction block.
   */
  def currentSession: Session = Session.currentSession

  /**
   * Returns the thread-local Connection, or throws an exception if there is none.
   *
   * A connection is always set within an inTransaction block.
   */
  def currentConnection: Connection = currentSession.connection

  /** Provide a transaction for the block to execute in */
  protected def transactionBlock[A](block: Connection => A): A
}
