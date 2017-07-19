package controllers.auth

import javax.inject.Inject
import play.api.mvc.{ActionBuilder,AnyContent,BodyParsers,Request,Result}
import scala.concurrent.{ExecutionContext,Future}

class ApiAuthorizedAction @Inject() (
  apiTokenFactory: ApiTokenFactory,
  val bodyParser: BodyParsers.Default,
  ec: ExecutionContext
) {
  def apply(authority: Authority) = new ActionBuilder[ApiAuthorizedRequest, AnyContent] {
    override def parser = bodyParser
    override implicit def executionContext = ec

    override def invokeBlock[A](request: Request[A], block: (ApiAuthorizedRequest[A] => Future[Result])): Future[Result] = {
      /*
       * We special-case AuthorizedRequest[A] to short-circuit auth, so we can
       * write tests that don't hit UserFactory.
       *
       * We can't use overloading (because Request is a trait) or matching
       * (because of type erasure), but we can prove this is type-safe.
       *
       * [adam, 2017-07-18] check this is still needed; it's for Play 2.3.
       */
      if (request.isInstanceOf[ApiAuthorizedRequest[_]]) {
        block(request.asInstanceOf[ApiAuthorizedRequest[A]])
      } else {
        apiTokenFactory.loadAuthorizedApiToken(request, authority).flatMap {
          case Left(plainResult) => Future(plainResult)
          case Right(apiToken) => block(new ApiAuthorizedRequest(request, apiToken))
        }
      }
    }
  }
}
