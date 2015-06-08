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
  protected def minCreatedAt: Timestamp = new Timestamp(System.currentTimeMillis - MaxSessionAgeInMs)

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

  /** Creates a Session in the database. */
  def create(userId: Long, ip: String): Future[Session]

  /** Destroys a Session from the database.
    *
    * This is a no-op if the Session is not already in the database.
    */
  def destroy(id: UUID): Future[Unit]

  /** Destroys expired Sessions for the given user.
    *
    * We only destroy a single User's Sessions at a time, so we can guarantee
    * that no one query will take too long.
    */
  def destroyExpiredSessionsForUserId(userId: Long): Future[Unit]
}

trait DbSessionBackend extends SessionBackend with DbBackend {
  import databaseApi._

  private lazy val byIdCompiled = Compiled { (id: Rep[UUID]) => Sessions.filter(_.id === id) }

  private lazy val byUserIdAndMaxCreatedAtCompiled = Compiled { (userId: Rep[Long], maxCreatedAt: Rep[Timestamp]) =>
    Sessions
      .filter(_.userId === userId)
      .filter(_.createdAt < maxCreatedAt)
  }

  private lazy val showWithUserCompiled = Compiled { (id: Rep[UUID], minCreatedAt: Rep[Timestamp]) =>
    for {
      s <- Sessions.filter(_.id === id).filter(_.createdAt >= minCreatedAt)
      u <- Users.filter(_.id === s.userId)
    } yield (s, u)
  }

  private lazy val updateCompiled = Compiled { (id: Rep[UUID]) =>
    for { s <- Sessions if s.id === id }
    yield (s.ip, s.updatedAt)
  }

  protected lazy val inserter = (Sessions returning Sessions)

  override def showWithUser(id: UUID) = database.option(showWithUserCompiled(id, minCreatedAt))

  override def update(id: UUID, attributes: Session.UpdateAttributes) = {
    val ip = InetAddress.getByName(attributes.ip)
    val updatedAt = new Timestamp(attributes.updatedAt.getTime())

    database.runUnit(updateCompiled(id).update((ip, updatedAt)))
  }

  override def create(userId: Long, ip: String) = {
    database.run(inserter.+=(Session(userId, ip)))
  }

  override def destroy(id: UUID) = database.delete(byIdCompiled(id))

  override def destroyExpiredSessionsForUserId(userId: Long) = {
    // Our minCreatedAt becomes maxCreatedAt in this query
    database.delete(byUserIdAndMaxCreatedAtCompiled(userId, minCreatedAt))
  }
}

object SessionBackend extends DbSessionBackend with org.overviewproject.database.DatabaseProvider
