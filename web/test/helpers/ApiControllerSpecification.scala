package controllers.api

import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.JsValue
import play.api.mvc.{Action,AnyContent,AnyContentAsEmpty,AnyContentAsJson,BodyParsers,Headers,RequestHeader,Result}
import play.api.http.{HeaderNames,Status}
import play.api.test.{DefaultAwaitTimeout,FakeRequest,ResultExtractors,FutureAwaits,StubPlayBodyParsersFactory}
import scala.concurrent.{ExecutionContext,Future}

import controllers.auth.{ApiAuthorizedAction,ApiAuthorizedRequest,ApiTokenFactory,Authority}
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
  trait ApiControllerScopeHelpers {
    implicit protected val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    val factory: Factory = PodoFactory

    protected def fakeApiToken = ApiToken("12345", new java.sql.Timestamp(0L), "user@example.org", "foo", Some(1L))

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

    def fakeControllerComponents: ApiControllerComponents = {
      val apiTokenFactory = new ApiTokenFactory {
        override def loadAuthorizedApiToken(request: RequestHeader, authority: Authority) = {
          Future.successful(Right(fakeApiToken))
        }
      }

      val parsersFactory = new StubPlayBodyParsersFactory {}
      val parsers = parsersFactory.stubPlayBodyParsers

      val apiAuthorizedAction = new ApiAuthorizedAction(
        apiTokenFactory,
        new BodyParsers.Default(parsers),
        materializer.executionContext
      )

      DefaultApiControllerComponents(
        apiAuthorizedAction,
        materializer.executionContext,
        parsers
      )
    }
  }

  trait ApiControllerScope extends Scope with ApiControllerScopeHelpers {
    lazy val apiToken = fakeApiToken
    lazy val request: ApiAuthorizedRequest[AnyContent] = fakeRequest
    def action: Action[AnyContent] = ???
    lazy val result: Future[Result] = action(request)
  }
}
