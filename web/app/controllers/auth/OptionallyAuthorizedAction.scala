package controllers.auth

import play.api.i18n.MessagesApi
import play.api.mvc.{ActionBuilder,AnyContent,BodyParser,BodyParsers,RequestHeader,Request,Result}
import scala.concurrent.{ExecutionContext,Future}
import javax.inject.Inject

class OptionallyAuthorizedAction @Inject() (
  sessionFactory: SessionFactory,
  val bodyParser: BodyParsers.Default,
  messagesApi: MessagesApi,
  ec: ExecutionContext
) {
  def apply(authority: Authority) = new ActionBuilder[OptionallyAuthorizedRequest, AnyContent] {
    override def parser = bodyParser
    override implicit def executionContext = ec

    override def invokeBlock[A](request: Request[A], block: (OptionallyAuthorizedRequest[A] => Future[Result])): Future[Result] = {
      /*
       * We special-case OptionallyAuthorizedRequest[A] to short-circuit auth,
       * so we can write tests that don't hit UserFactory.
       *
       * We can't use overloading (because Request is a trait) or matching
       * (because of type erasure), but we can prove this is type-safe.
       *
       * [adam, 2017-07-18] check this is still needed; it's for Play 2.3.
       */
      if (request.isInstanceOf[OptionallyAuthorizedRequest[_]]) {
        block(request.asInstanceOf[OptionallyAuthorizedRequest[A]])
      } else {
        sessionFactory
          .loadAuthorizedSession(request, authority) // Future[Either[Result,(Session,User)]]
          .map(_.right.toOption) // Future[Option[(Session,User)]]
          .map(new OptionallyAuthorizedRequest(request, messagesApi, _)) // Future[RequestHeader]
          .flatMap(block(_)) // Future[Result]
      }
    }
  }
}
