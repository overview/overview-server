package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ActionBuilder,Request,Result}
import scala.concurrent.Future

trait ApiAuthorizedAction {
  protected val apiTokenFactory: ApiTokenFactory

  def apply(authority: Authority) : ActionBuilder[ApiAuthorizedRequest] = {
    new ActionBuilder[ApiAuthorizedRequest] {
      override def invokeBlock[A](request: Request[A], block: (ApiAuthorizedRequest[A]) => Future[Result]) : Future[Result] = {
        /*
         * We special-case AuthorizedRequest[A] to short-circuit auth, so we can
         * write tests that don't hit UserFactory.
         *
         * We can't use overloading (because Request is a trait) or matching
         * (because of type erasure), but we can prove this is type-safe.
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
}
