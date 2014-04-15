package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{BodyParser,SimpleResult}
import play.api.mvc.BodyParsers.parse
import scala.concurrent.Future

import models.OverviewDatabase
import models.orm.{Session, User}

object AuthorizedBodyParser {
  def apply[A](authority: Authority)(parserGenerator: User => BodyParser[A]): BodyParser[A] = {
    parse.using { implicit request =>
      OverviewDatabase.inTransaction {
        SessionFactory.loadAuthorizedSession(request, authority) match {
          case Left(e) => parse.error(Future(e))
          case Right((session: Session, user: User)) => parserGenerator(user)
        }
      }
    }
  }
}
