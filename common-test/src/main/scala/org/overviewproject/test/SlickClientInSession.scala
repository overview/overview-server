package org.overviewproject.test

import scala.concurrent.{ ExecutionContext, Future }
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient

trait SlickClientInSession extends SlickClient {
  
  implicit val session: Session
  override implicit protected val executor = scala.concurrent.ExecutionContext.global
  
  override def db[A](block: Session => A)(implicit executor: ExecutionContext): Future[A] = Future {
    block(session)
  }

}