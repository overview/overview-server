package models

import java.sql.Connection
import slick.jdbc.JdbcBackend.{Session=>JSession}

import org.overviewproject.database.{HasDatabase,TransactionProvider}

/** Manages database connections for a Play application
 *  Implements the method to execute a block inside a 
 *  transaction
 */
object OverviewDatabase extends TransactionProvider with HasDatabase {
  private val slickDatabase = database.slickDatabase

  protected def transactionBlock[A](block: Connection => A): A = {
    withSlickSession { session =>
      session.withTransaction { block(session.conn) }
    }
  }

  def withSlickSession[A](block: JSession => A): A = {
    val session = slickDatabase.createSession()
    try {
      block(session)
    } finally {
      session.close()
    }
  }
}
