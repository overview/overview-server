package controllers

import java.sql.Connection
import play.api.Play.current
import play.api.db.DB
import play.api.mvc.{Action, AnyContent, BodyParser, BodyParsers, Request, Result }
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Session
import controllers.util.HttpsEnforcer
import models.orm.SquerylPostgreSqlAdapter


trait TransactionActionController extends HttpsEnforcer {
  protected type ActionWithConnection[A] = {
    def apply(request: Request[A], connection: Connection): Result
  }

  protected def ActionInTransaction[A](p: BodyParser[A])(f: ActionWithConnection[A]) = {
    HttpsAction(p) { implicit request =>
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
}
