package controllers.auth

import akka.stream.Materializer
import javax.inject.Inject
import play.api.mvc.{BodyParser,PlayBodyParsers,RequestHeader,Result}
import scala.concurrent.{ExecutionContext,Future}

import models.{Session, User}

class AuthorizedBodyParser @Inject() (
  sessionFactory: SessionFactory,
  playBodyParsers: PlayBodyParsers,
  executionContext: ExecutionContext,
  materializer: Materializer
) {
  def apply[A](authority: Authority)(parserGenerator: User => BodyParser[A]): BodyParser[A] = new BodyParser[A] {
    override def apply(request: RequestHeader) = {
      val futureBodyParser: Future[BodyParser[A]] = sessionFactory.loadAuthorizedSession(request, authority).map(_ match {
        case Left(e: Result) => playBodyParsers.error(Future.successful(e))
        case Right((session: Session, user: User)) => parserGenerator(user)
      })(executionContext)

      val bodyParser = playBodyParsers.flatten(futureBodyParser)(executionContext, materializer)
      bodyParser(request)
    }
  }
}
