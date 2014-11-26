package org.overviewproject.database

import java.sql.Connection
import javax.sql.{ DataSource => JDataSource }
import scala.slick.jdbc.JdbcBackend.{ Database => JDatabase, Session }

object Database extends TransactionProvider {

  def transactionBlock[A](block: Connection => A): A = {
    DB.withTransaction(block)
  }

  private lazy val db = JDatabase.forDataSource(DB.getDataSource()) 
  
  def withSlickSession[A](block: Session => A): A = {
    db.withSession(block)
  }
}