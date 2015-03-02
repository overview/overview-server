package controllers.backend

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.Future

import models.{Session,User}
import models.tables.{Sessions,Users}
import org.overviewproject.postgres.InetAddress

trait SessionBackend extends Backend {
  /** Maximum age of a session.
    *
    * This Backend will never return a Session older than this; it will return
    * None instead.
    */
  val MaxSessionAgeInMs: Long = 30L * 86400 * 1000 // 30 days

  /** Minimum createdAt for a non-expired session (at call time). */
  protected def minCreatedAt: Timestamp = new Timestamp(new java.util.Date().getTime() - MaxSessionAgeInMs)

  /** Finds a Session.
    *
    * Returns None if the session is expired -- even if it exists in the
    * database.
    */
  def showWithUser(id: UUID): Future[Option[(Session,User)]]

  /** Sets the ip and updatedAt on a Session in the database.
    *
    * Amounts to a no-op if the change is too minor -- i.e., the ip has not
    * changed and updatedAt has not changed by even 10 minutes.
    */
  def update(id: UUID, attributes: Session.UpdateAttributes): Future[Unit]
}

trait DbSessionBackend extends SessionBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  private lazy val showWithUserCompiled = Compiled { (id: Column[UUID], minCreatedAt: Column[Timestamp]) =>
    for {
      s <- Sessions.filter(_.id === id).filter(_.createdAt >= minCreatedAt)
      u <- Users.filter(_.id === s.userId)
    } yield (s, u)
  }

  private lazy val updateCompiled = Compiled { (id: Column[UUID]) =>
    for { s <- Sessions if s.id === id }
    yield (s.ip, s.updatedAt)
  }

  override def showWithUser(id: UUID) = firstOption(showWithUserCompiled(id, minCreatedAt))

  override def update(id: UUID, attributes: Session.UpdateAttributes) = db { session =>
    val ip = InetAddress.getByName(attributes.ip)
    val updatedAt = new Timestamp(attributes.updatedAt.getTime())

    updateCompiled(id).update((ip, updatedAt))(session)
  }
}

object SessionBackend extends DbSessionBackend with DbBackend
