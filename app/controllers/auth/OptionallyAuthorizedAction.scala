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
  def apply[A](bodyParser: BodyParser[A], aAuthority: Authority)(aBlock: OptionallyAuthorizedRequest[A] => Result): OptionallyAuthorizedAction[A] = new OptionallyAuthorizedAction[A] {
    override def parser = bodyParser
    override val authority = aAuthority
    override val block = aBlock
  }

  def apply(authority: Authority)(block: OptionallyAuthorizedRequest[AnyContent] => Result): OptionallyAuthorizedAction[AnyContent] = {
    apply(BodyParsers.parse.anyContent, authority)(block)
  }
}
