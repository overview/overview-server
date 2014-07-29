package models

import java.sql.Connection
import play.api.Play.current
import play.api.db.DB
import scala.slick.jdbc.JdbcBackend.{Database,Session}

import org.overviewproject.database.TransactionProvider

/** Manages database connections for a Play application
 *  Implements the method to execute a block inside a 
 *  transaction
 */
object OverviewDatabase extends TransactionProvider {
  protected def transactionBlock[A](block: Connection => A): A = {
    DB.withTransaction(connection => block(connection))
  }

  private lazy val db = Database.forDataSource(DB.getDataSource())

  def withSlickSession[A](block: Session => A): A = {
    db.withSession(block)
  }
}
