package controllers

import java.sql.Connection
import org.squeryl.PrimitiveTypeMode._
import play.api.Play
import play.api.Play.current
import play.api.mvc.{Action, AnyContent, BodyParser, BodyParsers, Controller, Request, RequestHeader, PlainResult}
import scala.util.control.Exception.catching

import controllers.auth.{Authority,UserFactory}
import models.orm.{DocumentSet,Document}
import models.OverviewUser

trait BaseController extends Controller with TransactionActionController {
  def anyUser = controllers.auth.Authorities.anyUser
  def userOwningDocumentSet(id: Long) = controllers.auth.Authorities.userOwningDocumentSet(id)
  def userOwningDocument(id: Long) = controllers.auth.Authorities.userOwningDocument(id)

  protected def authorizedAction[A](p: BodyParser[A], authority: Authority)(f: OverviewUser => ActionWithConnection[A]): Action[A] = {
    ActionInTransaction(p) { (request: Request[A], connection: Connection) =>
      val user = UserFactory.loadUser(request, authority)

      user.fold(
        errorResult => errorResult,
        user => f(user)(request, connection)
      )
    }
  }

  protected def authorizedAction(authority: Authority)(f: OverviewUser => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    authorizedAction(BodyParsers.parse.anyContent, authority)(f)
  }

  protected def optionallyAuthorizedAction[A](p: BodyParser[A])(f: Option[OverviewUser] => ActionWithConnection[A]): Action[A] = {
    ActionInTransaction(p) { (request: Request[A], connection: Connection) =>
      val user = UserFactory.loadUser(request, anyUser)
      f(user.right.toOption)(request, connection)
    }
  }

  protected def optionallyAuthorizedAction(f: Option[OverviewUser] => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    optionallyAuthorizedAction(BodyParsers.parse.anyContent)(f)
  }
}
