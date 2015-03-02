package controllers.backend

import java.sql.Timestamp
import scala.concurrent.Future

import models.User
import models.tables.Users

trait UserBackend extends Backend {
  /** Updates lastActivityAt and lastActivityIp. */
  def updateLastActivity(id: Long, ip: String, at: Timestamp): Future[Unit]
}

trait DbUserBackend extends UserBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  private lazy val updateLastActivityCompiled = Compiled { (id: Column[Long]) =>
    for { user <- Users.filter(_.id === id) }
    yield (user.lastActivityIp, user.lastActivityAt)
  }

  override def updateLastActivity(id: Long, ip: String, at: Timestamp) = db { session =>
    updateLastActivityCompiled(id).update((Some(ip), Some(at)))(session)
  }
}

object UserBackend extends DbUserBackend with DbBackend
