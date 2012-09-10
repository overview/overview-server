package controllers

import java.sql.Connection
import org.squeryl.Session
import org.squeryl.PrimitiveTypeMode._
import play.api.mvc.{Action,BodyParser,BodyParsers,Request,Result,AnyContent}
import play.api.db.DB
import play.api.Play.current

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
