package controllers.auth

import java.util.{Date,UUID}
import java.sql.Timestamp
import javax.inject.Inject
import play.api.mvc.{RequestHeader, Result}
import scala.util.Success
import scala.util.control.Exception.catching
import scala.concurrent.{ExecutionContext,Future}

import controllers.backend.{DbSessionBackend,DbUserBackend,SessionBackend,UserBackend}
import models.{Session,User}
import com.overviewdocs.models.UserRole

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
  * For single-user mode, just stub out this trait.
  */
class SessionFactory @Inject() (
  authConfig: AuthConfig,
  sessionBackend: SessionBackend,
  userBackend: UserBackend,
  ec: ExecutionContext
) {
  private implicit def executionContext = ec

  /** Determines when to log user actions to the database and when to skip it.
    *
    * Logging to the database is useful: user.lastActivityAt and
    * user.lastActivityIp show up in our admin interface, and surely
    * session.updatedAt will come in handy sometime. On the other hand, every
    * endpoint hits this code path. If we log every time, we get big slowdowns.
    *
    * So we log only if the IP address would change or the time would change by
    * more than this amount.
    */
  private val ActivityLoggingResolutinInMs = 5L * 60L * 1000L

  private def loadSingleUserAuthorizedSession(request: RequestHeader, authority: Authority): Future[Either[Result,(Session,User)]] = {
    val session = Session(1L, request.remoteAddress)
    val user = User(1L, "admin@overviewdocs.com", role=UserRole.Administrator, treeTooltipsEnabled=false)
    Future.successful(Right((session, user)))
  }

  /** Returns either a Result (no access) or a (Session,User) (access).
    *
    * See the class documentation for details.
    */
  def loadAuthorizedSession(request: RequestHeader, authority: Authority): Future[Either[Result,(Session,User)]] = {
    val f = if (authConfig.isMultiUser) {
      loadMultiUserAuthorizedSession _
    } else {
      loadSingleUserAuthorizedSession _
    }
    f(request, authority)
  }

  private def maybeLogActivity(request: RequestHeader, session: Session, user: User): Future[Unit] = {
    val now: Long = new Date().getTime()

    val future1 = if (session.ip.value != request.remoteAddress
        || now > session.updatedAt.getTime + ActivityLoggingResolutinInMs) {
      val attributes = Session.UpdateAttributes(request.remoteAddress, new Date(now))
      sessionBackend.update(session.id, attributes)
    } else {
      Future.successful(())
    }

    val future2 = if (user.lastActivityIp != Some(request.remoteAddress)
        || now > user.lastActivityAt.map(_.getTime).getOrElse(0L) + ActivityLoggingResolutinInMs) {
      userBackend.updateLastActivity(user.id, request.remoteAddress, new Timestamp(now))
    } else {
      Future.successful(())
    }

    for {
      _ <- future1
      _ <- future2
    } yield ()
  }

  private def loadMultiUserAuthorizedSession(request: RequestHeader, authority: Authority) : Future[Either[Result,(Session,User)]] = {
    def unauthenticated = AuthResults.authenticationFailed(request)
    def unauthorized = AuthResults.authorizationFailed(request)

    val sessionIdString: Either[Result,String] = request.session.get(SessionFactory.SessionIdKey)
      .toRight(unauthenticated)

    val sessionId: Either[Result,UUID] = sessionIdString
      .right.flatMap((s: String) => catching(classOf[IllegalArgumentException]).opt(UUID.fromString(s))
      .toRight(unauthenticated))

    val sessionAndUser: Future[Either[Result,(Session,User)]] = sessionId
      .fold(
        (result: Result) => Future.successful(Left(result)),
        (id: UUID) => sessionBackend.showWithUser(id).map(_.toRight(unauthenticated))
      )

    val validSessionAndUser: Future[Either[Result,(Session,User)]] = sessionAndUser
      .flatMap(_.fold(
        (result: Result) => Future.successful(Left(result)),
        (su: (Session, User)) => authority(su._2).map(Either.cond(_, su, unauthorized))
      ))

    val loggedSessionAndUser: Future[Either[Result,(Session,User)]] = validSessionAndUser.andThen {
      case Success(Right(su)) => maybeLogActivity(request, su._1, su._2)
    }

    loggedSessionAndUser
  }
}

object SessionFactory {
  private[auth] val SessionIdKey = AuthResults.SessionIdKey // TODO find a sensible place for this constant
}
