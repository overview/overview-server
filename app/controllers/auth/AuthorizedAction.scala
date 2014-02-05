package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import scala.concurrent.Future

import models.{OverviewDatabase,OverviewUser}

object AuthorizedAction {
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
            UserFactory.loadUser(request, authority) match {
              case Left(plainResult) => Future(plainResult)
              case Right(user) => block(new AuthorizedRequest(request, user))
            }
          }
        }
      }
    }
  }
}
