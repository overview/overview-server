package controllers

import java.sql.Connection
import jp.t2v.lab.play20.auth.Auth
import org.squeryl.Session
import org.squeryl.PrimitiveTypeMode._
import play.api.db.DB
import play.api.mvc.{Controller,Action,BodyParser,BodyParsers,Request,Result,AnyContent}
import play.api.Play.current

import models.orm.User
import models.orm.SquerylPostgreSqlAdapter

trait BaseController extends Controller with Auth with AuthConfigImpl {
  type ActionWithConnection[A] = {
    def apply(request: Request[A], connection: Connection): Result
  }

  protected def ActionInTransaction[A](p: BodyParser[A])(f: ActionWithConnection[A]) = {
    Action(p) { implicit request =>
      DB.withTransaction { implicit connection =>
        val adapter = new SquerylPostgreSqlAdapter()
        val session = new Session(connection, adapter)
        using(session) { // sets thread-local variable
          f(request, connection)
        }
      }
    }
  }

  protected def ActionInTransaction(f: ActionWithConnection[AnyContent]): Action[AnyContent] = {
    ActionInTransaction(BodyParsers.parse.anyContent)(f)
  }

  protected def authorizedAction[A](p: BodyParser[A], authority: Authority)(f: User => ActionWithConnection[A]): Action[A] = {
    ActionInTransaction(p) { (request: Request[A], connection: Connection) =>
      authorized(authority)(request).right.map(user => f(user)(request, connection)).merge
    }
  }

  protected def authorizedAction(authority: Authority)(f: User => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    authorizedAction(BodyParsers.parse.anyContent, authority)(f)
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
   * Actions may be defined liek this:
   *
   * def show(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)) {
   *   user => (request, connection) => { ... do something ... }
   * }
   */
  protected def userOwningDocumentSet(id: Long) : Authority = { user => user.documentSets.where((ds) => ds.id === id).nonEmpty }
}
