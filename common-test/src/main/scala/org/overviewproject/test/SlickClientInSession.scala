package org.overviewproject.test

import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient

trait SlickClientInSession extends SlickClient {
  import scala.concurrent.ExecutionContext.Implicits.global
  
  implicit val session: Session
  
  override def db[A](block: Session => A): Future[A] = Future {
    block(session)
  }

}