package controllers.auth

import java.util.Date
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ActionBuilder, RequestHeader, Request, Result}
import play.api.Play
import scala.concurrent.Future

import models.OverviewDatabase
import models.orm.{Session, User, UserRole}
import models.orm.stores.{SessionStore, UserStore}
import models.orm.finders.UserFinder

trait AuthorizedAction {
  protected val sessionFactory: SessionFactory
  protected def logActivity(request: RequestHeader, session: Session, user: User): (Session, User)

  /** Loads (Session,User) if possible.
    *
    * Must be called from within a transaction.
    */
  private def loadAndLogActivity(request: RequestHeader, authority: Authority): Either[Result,(Session,User)] = {
    sessionFactory.loadAuthorizedSession(request, authority) match {
      case Left(plainResult) => Left(plainResult)
      case Right((session,user)) => Right(logActivity(request, session, user))
    }
  }

  def apply(authority: Authority) : ActionBuilder[AuthorizedRequest] = {
    new ActionBuilder[AuthorizedRequest] {
      override def invokeBlock[A](request: Request[A], block: (AuthorizedRequest[A]) => Future[Result]) : Future[Result] = {
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
          OverviewDatabase.inTransaction { loadAndLogActivity(request, authority) } match {
            case Left(plainResult) => Future(plainResult)
            case Right((session,user)) => block(new AuthorizedRequest(request, session, user))
          }
        }
      }
    }
  }

  def inTransaction(authority: Authority) : ActionBuilder[AuthorizedRequest] = {
    new ActionBuilder[AuthorizedRequest] {
      override def invokeBlock[A](request: Request[A], block: (AuthorizedRequest[A]) => Future[Result]) : Future[Result] = {
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
            loadAndLogActivity(request, authority) match {
              case Left(plainResult) => Future(plainResult)
              case Right((session,user)) => block(new AuthorizedRequest(request, session, user))
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
