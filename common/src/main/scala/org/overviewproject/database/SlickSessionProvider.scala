package org.overviewproject.database

import scala.concurrent.{ blocking, ExecutionContext, Future }
import org.overviewproject.database.Slick.simple.Session

trait SlickSessionProvider {

  protected def db[A](block: Session => A)(implicit executor: ExecutionContext): Future[A] = Future {
    blocking {
      Database.withSlickSession { session =>
        block(session)
      }
    }
  }
}