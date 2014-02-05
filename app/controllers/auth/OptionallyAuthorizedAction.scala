package controllers.auth

import play.api.mvc._
import scala.concurrent.Future

import models.{OverviewDatabase,OverviewUser}

object OptionallyAuthorizedAction {
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
            val maybeUser = UserFactory.loadUser(request, authority).right.toOption
            block(new OptionallyAuthorizedRequest(request, maybeUser))
          }
        }
      }
    }
  }

}
