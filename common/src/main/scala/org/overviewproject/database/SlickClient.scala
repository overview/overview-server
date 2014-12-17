package org.overviewproject.database

import scala.concurrent.{ ExecutionContext, Future }
import org.overviewproject.database.Slick.simple._

trait SlickClient {
  protected implicit val executor: ExecutionContext
  
  protected def db[A](block: Session => A)(implicit executor: ExecutionContext): Future[A]
}