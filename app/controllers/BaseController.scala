package controllers

import java.sql.Connection
import org.squeryl.PrimitiveTypeMode._
import play.api.Play
import play.api.Play.current
import play.api.mvc.{Action, AnyContent, BodyParser, BodyParsers, Controller, Request, RequestHeader, PlainResult}
import scala.util.control.Exception.catching

import models.orm.{DocumentSet,Document}
import models.OverviewUser

trait BaseController extends Controller with TransactionActionController with AuthConfigImpl {
  protected def authorizedAction[A](p: BodyParser[A], authority: Authority)(f: OverviewUser => ActionWithConnection[A]): Action[A] = {
    ActionInTransaction(p) { (request: Request[A], connection: Connection) =>
      val user = userPassingAuthorization(request, authority)

      user.fold(
        errorResult => errorResult,
        user => f(user)(request, connection)
      )
    }
  }

  protected def userPassingAuthorization(request: RequestHeader, authority: Authority) : Either[PlainResult, User] = {
    userAuthenticatedAuthorizedAndRecordedAsRight(request, authority)
  }

  /** Returns a Right[OverviewUser] if the user exists. */
  private def userAuthenticatedAsRight(request: RequestHeader) = {
    restoreUser(request).toRight(authenticationFailed(request))
  }

  /** Returns a Right[OverviewUser] if the user is authorized under the authority. */
  private def userAuthorizedAsRight(request: RequestHeader, user: OverviewUser, authority: Authority) = {
    Either.cond(authority(user), user, authorizationFailed(request))
  }

  /** Returns a Right[OverviewUser], saved to the DB, if the user is authenticated and authorized. */
  private def userAuthenticatedAuthorizedAndRecordedAsRight(request: RequestHeader, authority: Authority) = {
    userAuthenticatedAsRight(request)
      .right.flatMap(user => userAuthorizedAsRight(request, user, authority))
      .right.map(user => user.withActivityRecorded(request.remoteAddress, new java.util.Date()).save)
  }

  protected def authorizedAction(authority: Authority)(f: OverviewUser => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    authorizedAction(BodyParsers.parse.anyContent, authority)(f)
  }

  private def request2user(request: RequestHeader) : Option[OverviewUser] = for {
    userIdString <- request.session.get(AuthConfigImpl.AuthUserIdKey)
    userId <- catching(classOf[NumberFormatException]).opt(userIdString.toLong)
    user <- OverviewUser.findById(userId)
  } yield user

  protected def optionallyAuthorizedAction[A](p: BodyParser[A])(f: Option[OverviewUser] => ActionWithConnection[A]): Action[A] = {
    ActionInTransaction(p) { (request: Request[A], connection: Connection) =>
      val user = restoreUser(request)
      val recordedUser = user.map(u => u
        .withActivityRecorded(request.remoteAddress, new java.util.Date())
        .save
      )
      f(recordedUser)(request, connection)
    }
  }

  protected def optionallyAuthorizedAction(f: Option[OverviewUser] => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    optionallyAuthorizedAction(BodyParsers.parse.anyContent)(f)
  }

  // copied from play20-auth
  private def restoreUser(implicit request: RequestHeader): Option[OverviewUser] = {
    if (BaseController.isMultiUser) {
      request2user(request)
    } else {
      val user = OverviewUser.findById(1L).getOrElse(throw new Exception("Singleton user not found")).asNormalUser
      Some(user)
    }
  }

  /*
   * Actions may be defined like this:
   *
   * def index = authorizedAction(anyUser) { user => (request, connection) =>
   *  // do something
   * }
   */
  protected def anyUser : Authority = { user => true }

  /*
   * Actions may be defined like this:
   *
   * def show(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)) {
   *   user => (request, connection) => { ... do something ... }
   * }
   */
  protected def userOwningDocumentSet(id: Long) : Authority = { user =>
    user.isAllowedDocumentSet(id)
  }
  
  protected def userOwningDocument(id: Long) : Authority = { user =>
    user.isAllowedDocument(id)
  }
}

object BaseController {
  private lazy val isMultiUser : Boolean = {
    Play.configuration.getBoolean("overview.multi_user").getOrElse(true)
  }
}
