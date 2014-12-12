package org.overviewproject.database

import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._

trait SlickClient {
  protected def db[A](block: Session => A): Future[A]
}