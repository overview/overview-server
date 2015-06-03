package org.overviewproject.database

import slick.dbio.DBIO
import slick.jdbc.JdbcBackend.{ Database => SlickDatabase, Session }

trait SlickSessionProvider extends SlickClient {
  def slickDb = SlickSessionProvider.slickDbSingleton

  /** Runs the given action, blocking until it returns a result.
    *
    * DO NOT USE. This is to make deprecated code less-deprecated, quickly.
    */
  def runBlocking[R](action: DBIO[R]): R = scala.concurrent.blocking {
    scala.concurrent.Await.result(
      slickDb.run(action),
      scala.concurrent.duration.Duration.Inf
    )
  }

  override def blockingDb[A](block: Session => A): A = slickDb.withSession(block)
}

object SlickSessionProvider {
  // Connect to the database just once
  lazy val slickDbSingleton = SlickDatabase.forDataSource(DB.getDataSource())
}
