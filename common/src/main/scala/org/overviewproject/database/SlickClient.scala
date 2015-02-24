package org.overviewproject.database

import scala.concurrent.{ ExecutionContext, Future }
import scala.slick.jdbc.JdbcBackend.Session

trait SlickClient {
  protected def db[A](block: Session => A)(implicit executor: ExecutionContext): Future[A]
  
  protected def withTransaction[A](block: Session => A)(implicit session: Session): A = {
    val connection = session.conn
    
    if (connection.getAutoCommit) {
      connection.setAutoCommit(false)
      
      val r = block(session)
      
      connection.commit
      connection.setAutoCommit(true)
      r
    }
    else block(session)
  }
}
