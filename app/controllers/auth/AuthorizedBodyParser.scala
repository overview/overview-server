package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{BodyParser,SimpleResult}
import play.api.mvc.BodyParsers.parse
import scala.concurrent.Future

import models.{OverviewDatabase,OverviewUser}

object AuthorizedBodyParser {

  def apply[A](authority: Authority)(parserGenerator: OverviewUser => BodyParser[A]): BodyParser[A] =
    parse.using { implicit request =>
      val user: Either[SimpleResult, OverviewUser] = OverviewDatabase.inTransaction { UserFactory.loadUser(request, authority) }
      user match {
        case Left(e) => parse.error(Future(e))
        case Right(user) => parserGenerator(user)
      }
    }
}
