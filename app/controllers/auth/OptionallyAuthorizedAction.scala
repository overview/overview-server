package controllers.auth

import play.api.mvc.{ ActionBuilder, Request, SimpleResult }
import play.api.Play
import scala.concurrent.Future

import models.OverviewDatabase

trait OptionallyAuthorizedAction {
  protected val sessionFactory: SessionFactory

  def apply(authority: Authority) : ActionBuilder[OptionallyAuthorizedRequest] = {
    new ActionBuilder[OptionallyAuthorizedRequest] {
      override protected def invokeBlock[A](request: Request[A], block: (OptionallyAuthorizedRequest[A]) => Future[SimpleResult]) : Future[SimpleResult] = {
        OverviewDatabase.inTransaction {
          /*
           * We special-case OptionallyAuthorizedRequest[A] to short-circuit
           * auth, so we can write tests that don't hit UserFactory.
           *
           * We can't use overloading (because Request is a trait) or matching
           * (because of type erasure), but we can prove this is type-safe.
           */
          if (request.isInstanceOf[OptionallyAuthorizedRequest[_]]) {
            block(request.asInstanceOf[OptionallyAuthorizedRequest[A]])
          } else {
            val maybeSessionAndUser = sessionFactory.loadAuthorizedSession(request, authority).right.toOption
            val newRequest = new OptionallyAuthorizedRequest(request, maybeSessionAndUser)
            block(newRequest)
          }
        }
      }
    }
  }
}

object OptionallyAuthorizedAction extends OptionallyAuthorizedAction {
  private val isMultiUser = Play.current.configuration.getBoolean("overview.multi_user").getOrElse(true)

  override val sessionFactory = {
    if (isMultiUser) {
      SessionFactory
    } else {
      SingleUserSessionFactory
    }
  }
}
