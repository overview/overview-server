package controllers.auth

import play.api.mvc.BodyParser
import play.api.mvc.BodyParsers.parse
import models.OverviewUser
import play.api.mvc.Result
import models.OverviewDatabase

object AuthorizedBodyParser {

  def apply[A](authority: Authority)(parserGenerator: OverviewUser => BodyParser[A]): BodyParser[A] =
    parse.using { implicit request =>
      val user: Either[Result, OverviewUser] = OverviewDatabase.inTransaction { UserFactory.loadUser(request, authority) }
      user match {
        case Left(e) => parse.error(e)
        case Right(user) => parserGenerator(user)
      }
    }
}