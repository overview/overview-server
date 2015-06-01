package controllers.auth

import java.util.Date
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ActionBuilder, RequestHeader, Request, Result}
import play.api.Play
import scala.concurrent.Future

import models.OverviewDatabase
import models.{Session,User}
import models.orm.stores.{SessionStore, UserStore}
import models.orm.finders.UserFinder
import org.overviewproject.models.UserRole

trait AuthorizedAction {
  protected val sessionFactory: SessionFactory

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
          sessionFactory.loadAuthorizedSession(request, authority).flatMap(_ match {
            case Left(plainResult) => Future.successful(plainResult)
            case Right((session,user)) => block(new AuthorizedRequest(request, session, user))
          })
        }
      }
    }
  }

  def inTransaction(authority: Authority) : ActionBuilder[AuthorizedRequest] = {
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
          sessionFactory.loadAuthorizedSession(request, authority).flatMap(_ match {
            case Left(plainResult) => Future.successful(plainResult)
            case Right((session,user)) => OverviewDatabase.inTransaction {
              block(new AuthorizedRequest(request, session, user))
            }
          })
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
}
