package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{BodyParser,Result}
import play.api.mvc.BodyParsers.parse
import play.api.Play
import scala.concurrent.Future

import models.OverviewDatabase
import models.{Session, User}

trait AuthorizedBodyParser {
  protected val sessionFactory: SessionFactory

  def apply[A](authority: Authority)(parserGenerator: User => BodyParser[A]): BodyParser[A] = {
    parse.using { implicit request =>
      OverviewDatabase.inTransaction {
        sessionFactory.loadAuthorizedSession(request, authority) match {
          case Left(e) => parse.error(Future(e))
          case Right((session: Session, user: User)) => parserGenerator(user)
        }
      }
    }
  }
}

object AuthorizedBodyParser extends AuthorizedBodyParser {
  private val isMultiUser = Play.current.configuration.getBoolean("overview.multi_user").getOrElse(true)

  override val sessionFactory = {
    if (isMultiUser) {
      SessionFactory
    } else {
      SingleUserSessionFactory
    }
  }
}
