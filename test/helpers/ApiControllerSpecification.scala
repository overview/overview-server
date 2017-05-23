package controllers.api

import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.JsValue
import play.api.mvc.{Action,AnyContent,AnyContentAsEmpty,AnyContentAsJson,Headers,Request,Result}
import play.api.http.{HeaderNames,Status}
import play.api.test.{DefaultAwaitTimeout,FakeRequest,ResultExtractors,FutureAwaits}
import scala.concurrent.{ExecutionContext,Future}

import controllers.auth.{ApiAuthorizedAction,ApiAuthorizedRequest}
import com.overviewdocs.models.ApiToken
import com.overviewdocs.test.factories.{Factory,PodoFactory}

trait ApiControllerSpecification
  extends test.helpers.InAppSpecification // for materializer -- needed for akka.stream
  with Mockito
  with JsonMatchers
  with HeaderNames
  with Status
  with DefaultAwaitTimeout
  with ResultExtractors
  with FutureAwaits
{
  trait TestController { self: ApiController =>
    override def messagesApi = new test.helpers.MockMessagesApi()
  }

  trait ApiControllerScope extends Scope {
    implicit protected val executionContext: ExecutionContext = play.api.libs.concurrent.Execution.defaultContext

    val factory: Factory = PodoFactory

    private def fakeApiToken = ApiToken("12345", new java.sql.Timestamp(0L), "user@example.org", "foo", Some(1L))

    def fakeRequest[T](body: T): ApiAuthorizedRequest[T] = {
      new ApiAuthorizedRequest(FakeRequest().withBody(body), fakeApiToken)
    }
    def fakeRequest(method: String, uri: String): ApiAuthorizedRequest[AnyContent] = {
      new ApiAuthorizedRequest(FakeRequest(method, uri).withBody(AnyContentAsEmpty), fakeApiToken)
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
