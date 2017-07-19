package controllers.auth

import play.api.i18n.MessagesApi
import play.api.mvc.{ActionBuilder,AnyContent,BodyParser,BodyParsers,RequestHeader,Request,Result}
import scala.concurrent.{ExecutionContext,Future}
import javax.inject.Inject

import models.{Session,User}

class AuthorizedAction @Inject() (
  sessionFactory: SessionFactory,
  val bodyParser: BodyParsers.Default,
  messagesApi: MessagesApi,
  ec: ExecutionContext
) {
  def apply(authority: Authority) = new ActionBuilder[AuthorizedRequest, AnyContent] {
    override def parser = bodyParser
    override def executionContext = ec

    override def invokeBlock[A](request: Request[A], block: (AuthorizedRequest[A] => Future[Result])): Future[Result] = {
      /*
       * We special-case AuthorizedRequest[A] to short-circuit auth, so we can
       * write tests that don't hit UserFactory.
       *
       * We can't use overloading (because Request is a trait) or matching
       * (because of type erasure), but we can prove this is type-safe.
       *
       * [adam, 2017-07-18] check this is still needed; it's for Play 2.3.
       */
      if (request.isInstanceOf[AuthorizedRequest[_]]) {
        block(request.asInstanceOf[AuthorizedRequest[A]])
      } else {
        sessionFactory.loadAuthorizedSession(request, authority).flatMap(_ match {
          case Left(plainResult) => Future.successful(plainResult)
          case Right((session,user)) => block(new AuthorizedRequest(request, messagesApi, session, user))
        })(executionContext)
      }
    }
  }
}
