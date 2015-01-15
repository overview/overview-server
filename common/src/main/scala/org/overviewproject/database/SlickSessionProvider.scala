package org.overviewproject.database

import scala.concurrent.{ blocking, ExecutionContext, Future }
import scala.slick.jdbc.JdbcBackend.{ Database => SlickDatabase }
import org.overviewproject.database.Slick.simple.Session

trait SlickSessionProvider extends SlickClient {

  private lazy val slickDb = SlickDatabase.forDataSource(DB.getDataSource())
  
  
  protected def db[A](block: Session => A)(implicit executor: ExecutionContext): Future[A] = Future {
    blocking {
      slickDb.withSession { session =>
        block(session)
      }
    }
  }
}