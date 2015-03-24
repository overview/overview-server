package controllers.backend

import java.sql.Timestamp
import scala.concurrent.Future

import models.User
import models.tables.Users
import org.overviewproject.models.tables.DocumentSetUsers

trait UserBackend extends Backend {
  /** Updates lastActivityAt and lastActivityIp. */
  def updateLastActivity(id: Long, ip: String, at: Timestamp): Future[Unit]

  /** Destroys a User, if that User exists. */
  def destroy(id: Long): Future[Unit]
}

trait DbUserBackend extends UserBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  private lazy val byId = Compiled { (id: Column[Long]) =>
    Users.filter(_.id === id)
  }

  private lazy val updateLastActivityCompiled = Compiled { (id: Column[Long]) =>
    for { user <- Users.filter(_.id === id) }
    yield (user.lastActivityIp, user.lastActivityAt)
  }

  override def updateLastActivity(id: Long, ip: String, at: Timestamp) = db { session =>
    updateLastActivityCompiled(id).update((Some(ip), Some(at)))(session)
  }

  override def destroy(id: Long) = db { session =>
    import scala.slick.jdbc.StaticQuery

    // One Big Query: simulate a transaction and avoid round trips
    val q = s"""
      DO $$$$
      DECLARE
        loids BIGINT[];
        loid BIGINT;
      BEGIN
        WITH x AS (
          DELETE FROM upload
          WHERE user_id = $id
          RETURNING contents_oid
        ), subdelete2 AS (
          DELETE FROM session
          WHERE user_id = $id
        ), subdelete3 AS (
          DELETE FROM "user"
          WHERE id = $id
        )
        SELECT COALESCE(ARRAY_AGG(contents_oid), ARRAY[]::BIGINT[])
        INTO loids
        FROM x;

        FOREACH loid IN ARRAY loids LOOP
          PERFORM lo_unlink(loid);
        END LOOP;
      END$$$$ LANGUAGE plpgsql;
    """
    StaticQuery.updateNA(q).apply(()).execute(session)
  }
}

object UserBackend extends DbUserBackend with DbBackend
