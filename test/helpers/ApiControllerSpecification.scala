package controllers.api

import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.JsValue
import play.api.mvc.{Action,AnyContent,AnyContentAsEmpty,AnyContentAsJson,Headers,Request,Result}
import play.api.http.{HeaderNames,Status}
import play.api.test.{DefaultAwaitTimeout,FakeRequest,ResultExtractors,FutureAwaits}
import scala.concurrent.Future

import controllers.auth.{ApiAuthorizedAction,ApiAuthorizedRequest}
import org.overviewproject.models.ApiToken
import org.overviewproject.test.factories.{Factory,PodoFactory}

trait ApiControllerSpecification
  extends Specification
  with Mockito
  with JsonMatchers
  with HeaderNames
  with Status
  with DefaultAwaitTimeout
  with ResultExtractors
  with FutureAwaits
{
  trait ApiControllerScope extends Scope {
    implicit protected val executionContext = scala.concurrent.ExecutionContext.Implicits.global

    val factory: Factory = PodoFactory

    private def fakeApiToken = ApiToken("12345", new java.sql.Timestamp(0L), "user@example.org", "foo", 1L)

    def fakeRequest[T](body: T): ApiAuthorizedRequest[T] = {
      new ApiAuthorizedRequest(FakeRequest().withBody(body), fakeApiToken)
    }
    def fakeJsonRequest(body: JsValue): ApiAuthorizedRequest[AnyContentAsJson] = {
      new ApiAuthorizedRequest(FakeRequest().withJsonBody(body), fakeApiToken)
    }
    def fakeRequest: ApiAuthorizedRequest[AnyContent] = fakeRequest(AnyContentAsEmpty)

    lazy val apiToken = fakeApiToken
    lazy val request: ApiAuthorizedRequest[AnyContent] = fakeRequest
    def action: Action[AnyContent] = ???
    lazy val result: Future[Result] = action(request)
  }
}
