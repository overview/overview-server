package controllers.auth

import java.sql.Connection
import play.api.mvc.{Action, AnyContent, BodyParser, BodyParsers, Request, Result}

import models.{OverviewDatabase,OverviewUser}

trait AuthorizedAction[A] extends Action[A] {
  val authority: Authority
  val block: (AuthorizedRequest[A] => Result)

  def apply(request: Request[A]): Result = {
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
          case Left(plainResult) => plainResult
          case Right(user) => block(new AuthorizedRequest(request, user))
        }
      }
    }
  }

  override def toString = {
    "AuthorizedAction(parser=" + parser + ", authority=" + authority + ")"
  }
}

object AuthorizedAction {
  /** Creates a new AuthorizedAction.
    *
    * The block will only be called when a logged-in user passes the
    * aAuthority check.
    */
  def apply[A](bodyParser: BodyParser[A], aAuthority: Authority)(aBlock: AuthorizedRequest[A] => Result): AuthorizedAction[A] = new AuthorizedAction[A] {
    override def parser = bodyParser
    override val authority = aAuthority
    override val block = aBlock
  }

  /** Creates a new AuthorizedAction[AnyContent], with a default BodyParser. */
  def apply(authority: Authority)(block: AuthorizedRequest[AnyContent] => Result): AuthorizedAction[AnyContent] = {
    apply(BodyParsers.parse.anyContent, authority)(block)
  }
}
