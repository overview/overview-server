package controllers.backend

import java.sql.Timestamp
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import models.User
import models.tables.Users
import models.pagination.{Page,PageRequest}
import com.overviewdocs.models.UserRole

trait UserBackend extends Backend {
  /** Returns a page of Users. */
  def indexPage(pageRequest: PageRequest): Future[Page[User]]

  /** Returns a User, if that User exists. */
  def showByEmail(email: String): Future[Option[User]]

  /** Creates a User from the given attributes.
    *
    * Throws Conflict if a user with this email address already exists.
    */
  def create(attributes: User.CreateAttributes): Future[User]

  /** Modifies a User, or does nothing if the User does not exist. */
  def updateIsAdmin(id: Long, isAdmin: Boolean): Future[Unit]

  /** Modifies a User, or does nothing if the User does not exist. */
  def updatePasswordHash(id: Long, passwordHash: String): Future[Unit]

  /** Updates lastActivityAt and lastActivityIp. */
  def updateLastActivity(id: Long, ip: String, at: Timestamp): Future[Unit]

  /** Destroys a User, if that User exists. */
  def destroy(id: Long): Future[Unit]
}

trait DbUserBackend extends UserBackend with DbBackend {
  import DbUserBackend.q
  import database.api._

  override def indexPage(pageRequest: PageRequest) = page(
    q.indexPageCompiled(pageRequest.offset, pageRequest.limit),
    q.indexLengthCompiled,
    pageRequest
  )

  override def showByEmail(email: String) = database.option(q.byEmail(email))

  override def create(attributes: User.CreateAttributes) = database.run(q.userInserter.+=(attributes))

  override def updateIsAdmin(id: Long, isAdmin: Boolean) = {
    val role = if (isAdmin) UserRole.Administrator else UserRole.NormalUser
    database.runUnit(q.updateRoleCompiled(id).update(role))
  }

  override def updatePasswordHash(id: Long, passwordHash: String) = {
    database.runUnit(q.updatePasswordHashCompiled(id).update(passwordHash))
  }

  override def updateLastActivity(id: Long, ip: String, at: Timestamp) = {
    database.runUnit(q.updateLastActivityCompiled(id).update((Some(ip), Some(at))))
  }

  override def destroy(id: Long) = {
    // One Big Query: simulate a transaction and avoid round trips
    database.runUnit(sqlu"""
      DO $$$$
      DECLARE
        loids BIGINT[];
        loid BIGINT;
      BEGIN
        WITH x AS (
          DELETE FROM upload
          WHERE user_id = #$id
          RETURNING contents_oid
        ), subdelete2 AS (
          DELETE FROM session
          WHERE user_id = #$id
        ), subdelete3 AS (
          DELETE FROM "user"
          WHERE id = #$id
        )
        SELECT COALESCE(ARRAY_AGG(contents_oid), ARRAY[]::BIGINT[])
        INTO loids
        FROM x;

        FOREACH loid IN ARRAY loids LOOP
          PERFORM lo_unlink(loid);
        END LOOP;
      END$$$$ LANGUAGE plpgsql;
    """)
  }
}

object DbUserBackend {
  protected object q {
    import com.overviewdocs.database.Slick.api._

    lazy val byEmail = Compiled { (email: Rep[String]) =>
      Users.filter(_.email === email)
    }

    lazy val byId = Compiled { (id: Rep[Long]) =>
      Users.filter(_.id === id)
    }

    lazy val updateRoleCompiled = Compiled { (id: Rep[Long]) =>
      Users.filter(_.id === id).map(_.role)
    }

    lazy val updatePasswordHashCompiled = Compiled { (id: Rep[Long]) =>
      Users.filter(_.id === id).map(_.passwordHash)
    }

    lazy val updateLastActivityCompiled = Compiled { (id: Rep[Long]) =>
      for { user <- Users.filter(_.id === id) }
      yield (user.lastActivityIp, user.lastActivityAt)
    }

    lazy val userInserter = (Users.map(_.createAttributes) returning Users)

    lazy val indexPageCompiled = Compiled { (offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
      Users
        .sortBy(u => (u.lastActivityAt.isDefined.desc, u.lastActivityAt.desc))
        .drop(offset)
        .take(limit)
    }

    lazy val indexLengthCompiled = Compiled { Users.length }
  }
}

object UserBackend extends DbUserBackend
