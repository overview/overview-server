package controllers.auth

import java.sql.Connection
import play.api.mvc.{Action, AnyContent, BodyParser, BodyParsers, Request, Result}

import models.{OverviewDatabase,OverviewUser}

trait OptionallyAuthorizedAction[A] extends Action[A] {
  val authority: Authority
  val block: (OptionallyAuthorizedRequest[A] => Result)

  def apply(request: Request[A]): Result = {
    OverviewDatabase.inTransaction {
      /*
       * We special-case OptionallyAuthorizedAction[A] to short-circuit auth.
       *
       * This helps with testing. It's type-safe, despite erasure.
       */
      if (request.isInstanceOf[OptionallyAuthorizedRequest[_]]) {
        block(request.asInstanceOf[OptionallyAuthorizedRequest[A]])
      } else {
        val optionalUser = UserFactory.loadUser(request, authority).right.toOption
        val optionallyAuthorizedRequest = new OptionallyAuthorizedRequest(request, optionalUser)
        block(optionallyAuthorizedRequest)
      }
    }
  }

  override def toString = {
    "OptionallyAuthorizedAction(parser=" + parser + ", authority=" + authority + ")"
  }
}

object OptionallyAuthorizedAction {
  /** Creates a new OptionallyAuthorizedAction.
    *
    * The OptionallyAuthorizedRequest's user will only be set when a logged-in
    * user passes the aAuthority check.
    */
  def apply[A](bodyParser: BodyParser[A], aAuthority: Authority)(aBlock: OptionallyAuthorizedRequest[A] => Result): OptionallyAuthorizedAction[A] = new OptionallyAuthorizedAction[A] {
    override def parser = bodyParser
    override val authority = aAuthority
    override val block = aBlock
  }

  /** Creates a new AuthorizedAction[AnyContent], with a default BodyParser. */
  def apply(authority: Authority)(block: OptionallyAuthorizedRequest[AnyContent] => Result): OptionallyAuthorizedAction[AnyContent] = {
    apply(BodyParsers.parse.anyContent, authority)(block)
  }
}
