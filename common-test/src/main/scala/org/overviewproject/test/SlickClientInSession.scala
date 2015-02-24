package org.overviewproject.test

import scala.slick.jdbc.JdbcBackend.Session

import scala.concurrent.{ ExecutionContext, Future }
import org.overviewproject.database.SlickClient

trait SlickClientInSession extends SlickClient {
  implicit val session: Session
  
  override def db[A](block: Session => A)(implicit executor: ExecutionContext): Future[A] = Future {
    block(session)
  }
}
