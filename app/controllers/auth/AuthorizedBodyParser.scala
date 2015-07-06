package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{BodyParser,Result}
import play.api.mvc.BodyParsers.parse
import scala.concurrent.Future

import models.{Session, User}

trait AuthorizedBodyParser {
  protected val sessionFactory: SessionFactory

  private def await[A](f: => Future[A]): A = scala.concurrent.blocking {
    scala.concurrent.Await.result(f, scala.concurrent.duration.Duration.Inf)
  }

  def apply[A](authority: Authority)(parserGenerator: User => BodyParser[A]): BodyParser[A] = {
    parse.using { implicit request =>
      await(sessionFactory.loadAuthorizedSession(request, authority)) match {
        case Left(e: Result) => parse.error(Future.successful(e))
        case Right((session: Session, user: User)) => parserGenerator(user)
      }
    }
  }
}

object AuthorizedBodyParser extends AuthorizedBodyParser {
  override val sessionFactory = {
    if (AuthConfig.isMultiUser) {
      SessionFactory
    } else {
      SingleUserSessionFactory
    }
  }
}
