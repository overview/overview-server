package models

import java.sql.Connection
import org.squeryl.{PrimitiveTypeMode, Session}
import play.api.Play.current
import play.api.db.DB

import org.overviewproject.postgres.SquerylPostgreSqlAdapter
import overview.database.TransactionProvider

/** Manages database connections for a Play application
 *  Implements the method to execute a block inside a 
 *  transaction
 */
object OverviewDatabase extends TransactionProvider {
  
  protected def transactionBlock[A](block: Connection => A): A =
    DB.withTransaction(connection => block(connection))
  }
