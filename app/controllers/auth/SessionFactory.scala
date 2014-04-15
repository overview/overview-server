package controllers.auth

import java.sql.Timestamp
import java.util.{Date,UUID}
import play.api.Play
import play.api.Play.current
import play.api.mvc.{RequestHeader, SimpleResult}
import scala.util.control.Exception.catching

import models.OverviewUser
import models.orm.{Session,User,UserRole}
import models.orm.finders.{SessionFinder,UserFinder}
import org.overviewproject.postgres.InetAddress

/** Restores and authorizes sessions.
  *
  * At the crux of this trait is the loadAuthorizedSession() method. It takes a
  * RequestHeader and Authority and returns a Right[(Session,User)].
  *
  * This involves several tasks:
  *
  * 1. Fetch the session ID from the cookie (not there? authentication failed)
  * 2. Fetch the session and user from the database (not both there? authentication failed)
  * 3. Check if the user is authorized for the task at hand (no? authorization failed)
  *
  * We do _not_ check whether the user has been confirmed. We assume we checked
  * that in a previous request, before creating and storing the Session.
  *
  * For single-user mode, we take a different approach. We always choose the
  * User with ID 1L from the database, updated to be non-admin; and we always
  * create a dummy Session with UUID 1L, marked as persisted in the database.
  * That way the session will never get written (and surely won't get
  * overwritten).
  */
trait SessionFactory {
  private[auth] val SessionIdKey = AuthResults.SessionIdKey // TODO find a sensible place for this constant

  trait Storage {
    def loadSessionAndUser(sessionId: UUID) : Option[(Session,User)]
    def loadUser(userId: Long) : Option[User]
  }
  protected val storage : SessionFactory.Storage
  protected val isMultiUser : Boolean

  /** Returns either a SimpleResult (no access) or a (Session,User) (access).
    *
    * See the class documentation for details.
    */
  def loadAuthorizedSession(request: RequestHeader, authority: Authority) : Either[SimpleResult,(Session,User)] = {
    if (isMultiUser) {
      loadMultiUserAuthorizedSession(request, authority)
    } else {
      loadSingleUserAuthorizedSession(request, authority)
    }
  }

  private def loadMultiUserAuthorizedSession(request: RequestHeader, authority: Authority) : Either[SimpleResult,(Session,User)] = {
    def unauthenticated = AuthResults.authenticationFailed(request)
    def unauthorized = AuthResults.authorizationFailed(request)

    request.session.get(SessionIdKey).toRight(unauthenticated)
      .right.flatMap((s: String) => catching(classOf[IllegalArgumentException]).opt(UUID.fromString(s)).toRight(unauthenticated))
      .right.flatMap((id: UUID) => storage.loadSessionAndUser(id).toRight(unauthenticated))
      .right.flatMap((x: (Session,User)) => Either.cond(authority(OverviewUser(x._2)), x, unauthorized))
  }

  private def loadSingleUserAuthorizedSession(request: RequestHeader, authority: Authority) : Either[SimpleResult,(Session,User)] = {
    val userId = 1L
    storage.loadUser(userId) match {
      case Some(user) => {
        val session = Session(
          id=new UUID(0L, 1L),
          userId=userId,
          ip=InetAddress.getByName(request.remoteAddress),
          createdAt=new Timestamp(new Date().getTime()),
          updatedAt=new Timestamp(new Date().getTime()) // a different object than createdAt, for isPersisted=true
        )
        Right((session,user.copy(role=UserRole.NormalUser)))
      }
      case None => Left(AuthResults.authorizationFailed(request))
    }
  }
}

object SessionFactory extends SessionFactory {
  object DatabaseStorage extends SessionFactory.Storage {
    override def loadSessionAndUser(sessionId: UUID) = {
      SessionFinder.byId(sessionId).withUsers.headOption
    }

    override def loadUser(userId: Long) = {
      UserFinder.byId(userId).headOption
    }
  }

  override protected val storage = DatabaseStorage
  override protected lazy val isMultiUser = Play.configuration.getBoolean("overview.multi_user").getOrElse(true)
}
