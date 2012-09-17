package controllers

import java.sql.Connection
import jp.t2v.lab.play20.auth.Auth
import org.squeryl.PrimitiveTypeMode._
import play.api.mvc.{Controller,Action,BodyParser,BodyParsers,Request,Result,AnyContent}
import play.api.Play.current

import models.orm.User

trait BaseController extends Controller with TransactionActionController with Auth with AuthConfigImpl {
  protected def authorizedAction[A](p: BodyParser[A], authority: Authority)(f: User => ActionWithConnection[A]): Action[A] = {
    ActionInTransaction(p) { (request: Request[A], connection: Connection) =>
      authorized(authority)(request).right.map(user => f(user)(request, connection)).merge
    }
  }

  protected def authorizedAction(authority: Authority)(f: User => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    authorizedAction(BodyParsers.parse.anyContent, authority)(f)
  }

  protected def optionallyAuthorizedAction[A](p: BodyParser[A])(f: Option[User] => ActionWithConnection[A]): Action[A] = {
    ActionInTransaction(p) { (request: Request[A], connection: Connection) =>
      f(restoreUser(request))(request, connection)
    }
  }

  protected def optionallyAuthorizedAction(f: Option[User] => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    optionallyAuthorizedAction(BodyParsers.parse.anyContent)(f)
  }

  // copy/pasted from play20-auth
  private def restoreUser[A](implicit request: Request[A]): Option[User] = for {
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
  protected def anyUser() : Authority = { user => true }

  /*
   * Actions may be defined like this:
   *
   * def show(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)) {
   *   user => (request, connection) => { ... do something ... }
   * }
   */
  protected def userOwningDocumentSet(id: Long) : Authority = { user => 
    user.documentSets.where((ds) => ds.id === id).nonEmpty 
  }
  
  protected def userOwningDocument(id: Long) : Authority = { user =>
    user.documentSets.where(ds => id in from(ds.documents)(d => select(d.id))).nonEmpty
  }
}
