package controllers

import java.sql.Connection
import jp.t2v.lab.play20.auth.Auth
import org.squeryl.PrimitiveTypeMode._
import play.api.Play.current
import play.api.mvc.{Action, AnyContent, BodyParser, BodyParsers, Controller, Request, RequestHeader, Result}

import models.orm.{DocumentSet,Document}
import models.OverviewUser

trait BaseController extends Controller with TransactionActionController with Auth with AuthConfigImpl {
  protected def authorizedAction[A](p: BodyParser[A], authority: Authority)(f: OverviewUser => ActionWithConnection[A]): Action[A] = {
    ActionInTransaction(p) { (request: Request[A], connection: Connection) =>
      authorized(authority)(request).right.map(user => {
        val recordedUser = user
          .withActivityRecorded(request.remoteAddress, new java.util.Date())
          .save

        f(recordedUser)(request, connection)
      }).merge
    }
  }

  protected def authorizedAction(authority: Authority)(f: OverviewUser => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    authorizedAction(BodyParsers.parse.anyContent, authority)(f)
  }

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

  // copy/pasted from play20-auth
  private def restoreUser(implicit request: RequestHeader): Option[OverviewUser] = for {
    sessionId <- request.session.get("sessionId")
    userId <- resolver.sessionId2userId(sessionId)
    user <- resolveUser(userId)
  } yield {
    resolver.prolongTimeout(sessionId, sessionTimeoutInSeconds)
    user
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
