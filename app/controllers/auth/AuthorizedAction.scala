package controllers.auth

import java.util.Date
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ActionBuilder, RequestHeader, Request, SimpleResult}
import play.api.Play
import scala.concurrent.Future

import models.OverviewDatabase
import models.orm.{Session, User, UserRole}
import models.orm.stores.{SessionStore, UserStore}
import models.orm.finders.UserFinder

trait AuthorizedAction {
  protected val sessionFactory: SessionFactory
  protected def logActivity(request: RequestHeader, session: Session, user: User): (Session, User)

  def apply(authority: Authority) : ActionBuilder[AuthorizedRequest] = {
    new ActionBuilder[AuthorizedRequest] {
      override protected def invokeBlock[A](request: Request[A], block: (AuthorizedRequest[A]) => Future[SimpleResult]) : Future[SimpleResult] = {
        OverviewDatabase.inTransaction {
          /*
           * We special-case AuthorizedRequest[A] to short-circuit auth, so we can
           * write tests that don't hit UserFactory.
           *
           * We can't use overloading (because Request is a trait) or matching
           * (because of type erasure), but we can prove this is type-safe.
           */
          if (request.isInstanceOf[AuthorizedRequest[_]]) {
            block(request.asInstanceOf[AuthorizedRequest[A]])
          } else {
            sessionFactory.loadAuthorizedSession(request, authority) match {
              case Left(plainResult) => Future(plainResult)
              case Right((session,user)) => {
                val (newSession, newUser) = logActivity(request, session, user)
                block(new AuthorizedRequest(request, newSession, newUser))
              }
            }
          }
        }
      }
    }
  }
}

object AuthorizedAction extends AuthorizedAction {
  private val isMultiUser = Play.current.configuration.getBoolean("overview.multi_user").getOrElse(true)

  override val sessionFactory = {
    if (isMultiUser) {
      SessionFactory
    } else {
      SingleUserSessionFactory
    }
  }

  override def logActivity(request: RequestHeader, session: Session, user: User) = {
    if (isMultiUser) {
      val ip = request.remoteAddress

      val newUser = UserStore.insertOrUpdate(user.copy(
        lastActivityAt = Some(new java.sql.Timestamp(new Date().getTime())),
        lastActivityIp = Some(ip)
      ))

      val newSession = SessionStore.insertOrUpdate(session.update(ip))

      (newSession, newUser)
    } else {
      (session, user)
    }
  }
}
