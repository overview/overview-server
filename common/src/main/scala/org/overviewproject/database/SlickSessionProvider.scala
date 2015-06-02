package org.overviewproject.database

import slick.jdbc.JdbcBackend.{ Database => SlickDatabase, Session }

trait SlickSessionProvider extends SlickClient {
  def slickDb = SlickSessionProvider.slickDbSingleton

  override def blockingDb[A](block: Session => A): A = slickDb.withSession(block)
}

object SlickSessionProvider {
  // Connect to the database just once
  lazy val slickDbSingleton = SlickDatabase.forDataSource(DB.getDataSource())
}
