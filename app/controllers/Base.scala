package controllers

import play.api.mvc.{Controller,Action,BodyParser,Request,Result}
import play.api.db.DB
import play.api.Play.current
import jp.t2v.lab.play20.auth.Auth

trait Base extends Controller with Auth with AuthConfigImpl {
  //def authorizedAction[A](p: BodyParser[A], authority: Authority)(f: User => Request[A] => java.sql.Connection => Result): Action[A] =
  //  Action(p) ( req =>
  //    DB.withTransaction { implicit connection =>
  //      authorized(authority)(req).right.map(u => f(u)(req)(connection)).merge
  //    }
  //  )


  /*
   * Actions may be defined like this:
   *
   * def index = authorizedAction(anyUser) { user => request =>
   *  // do something
   * }
   */
  def anyUser(user: User): Boolean = true
}
