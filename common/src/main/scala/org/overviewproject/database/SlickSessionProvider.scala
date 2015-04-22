package org.overviewproject.database

import scala.slick.jdbc.JdbcBackend.{ Database => SlickDatabase, Session }

trait SlickSessionProvider extends SlickClient {
  private lazy val slickDb = SlickDatabase.forDataSource(DB.getDataSource())

  override def blockingDb[A](block: Session => A): A = slickDb.withSession(block)
}
