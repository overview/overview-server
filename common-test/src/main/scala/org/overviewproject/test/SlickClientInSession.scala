package org.overviewproject.test

import slick.jdbc.JdbcBackend.Session

import org.overviewproject.database.SlickClient

trait SlickClientInSession extends SlickClient {
  implicit val session: Session

  override def blockingDb[A](block: Session => A): A = block(session)
}

object SlickClientInSession {
  def apply(aSession: Session) = new SlickClientInSession { override val session = aSession }
}
