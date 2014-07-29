package controllers.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import scala.slick.jdbc.JdbcBackend.Session

import models.OverviewDatabase

trait DbBackend {
  def db[A](block: Session => A): Future[A] = Future {
    OverviewDatabase.withSlickSession { session =>
      block(session)
    }
  }
}
