package controllers.auth

import java.sql.Connection
import play.api.mvc.{Action, AnyContent, BodyParser, BodyParsers, Request, Result}

import models.{OverviewDatabase,OverviewUser}

trait AuthorizedAction[A] extends Action[A] {
  val authority: Authority
  val block: (AuthorizedRequest[A] => Result)

  def apply(request: AuthorizedRequest[A]): Result = {
    /*
     * We special-case AuthorizedRequest[A] to short-circuit auth, so we can
     * write tests that don't hit UserFactory.
     */
    OverviewDatabase.inTransaction {
      block(request)
    }
  }

  def apply(request: Request[A]): Result = {
    OverviewDatabase.inTransaction {
      UserFactory.loadUser(request, authority) match {
        case Left(plainResult) => plainResult
        case Right(user) => {
          val authorizedRequest = new AuthorizedRequest(request, user)
          block(authorizedRequest)
        }
      }
    }
  }

  override def toString = {
    "AuthorizedAction(parser=" + parser + ", authority=" + authority + ")"
  }
}

object AuthorizedAction {
  def apply[A](bodyParser: BodyParser[A], aAuthority: Authority)(aBlock: AuthorizedRequest[A] => Result): AuthorizedAction[A] = new AuthorizedAction[A] {
    override def parser = bodyParser
    override val authority = aAuthority
    override val block = aBlock
  }

  def apply(authority: Authority)(block: AuthorizedRequest[AnyContent] => Result): AuthorizedAction[AnyContent] = {
    apply(BodyParsers.parse.anyContent, authority)(block)
  }
}
