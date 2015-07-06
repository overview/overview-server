package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ActionBuilder, RequestHeader, Request, Result}
import scala.concurrent.Future

import org.overviewproject.database.DeprecatedDatabase
import models.{Session,User}

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
            case Right((session,user)) => DeprecatedDatabase.inTransaction {
              block(new AuthorizedRequest(request, session, user))
            }
          })
        }
      }
    }
  }
}

object AuthorizedAction extends AuthorizedAction {
  override val sessionFactory = {
    if (AuthConfig.isMultiUser) {
      SessionFactory
    } else {
      SingleUserSessionFactory
    }
  }
}
