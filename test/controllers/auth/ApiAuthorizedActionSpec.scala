package controllers.auth

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{AnyContent, Request, RequestHeader, Results, Result}
import play.api.test.FakeRequest
import scala.concurrent.duration.Duration
import scala.concurrent.{Await,Future}
import scala.concurrent.ExecutionContext.Implicits.global

import org.overviewproject.models.ApiToken

class ApiAuthorizedActionSpec extends test.InAppSpecification with Mockito {
  trait BaseScope extends Scope {
    val mockApiTokenFactory = mock[ApiTokenFactory]
    val authority = mock[Authority]
    val request: RequestHeader = FakeRequest()

    var calledRequest: Option[RequestHeader] = None
    def block[A](r: ApiAuthorizedRequest[A]): Result = {
      calledRequest = Some(r)
      mock[Result]
    }

    lazy val actionBuilderFactory = new ApiAuthorizedAction {
      override protected val apiTokenFactory = mockApiTokenFactory
    }
    lazy val actionBuilder = actionBuilderFactory(authority)
    lazy val action = actionBuilder(block(_))

    def run = Await.result(action(request).run, Duration.Inf)
  }

  "should call the body directly when given an ApiAuthorizedRequest" in new BaseScope {
    // Unit tests need to short-circuit the database. That's how they become
    // unit-y. They use a stub ApiToken and skip the Authority.
    override val request = mock[ApiAuthorizedRequest[AnyContent]]
    action(request)

    calledRequest must beSome(request)
    there was no(mockApiTokenFactory).loadAuthorizedApiToken(request, authority)
  }

  "should return a plainResult if apiTokenFactory gives it" in new BaseScope {
    val result = Results.Unauthorized("foo")
    mockApiTokenFactory.loadAuthorizedApiToken(any[Request[_]], any[Authority]) returns Future(Left(result))

    run must beEqualTo(result)
    calledRequest must beNone
  }

  "should invoke the block if apiTokenFactory gives a Right" in new BaseScope {
    val apiToken = mock[ApiToken]
    mockApiTokenFactory.loadAuthorizedApiToken(any[Request[_]], any[Authority]) returns Future(Right(apiToken))

    run

    calledRequest must beSome.like { case r: ApiAuthorizedRequest[_] =>
      r.request.toString must beEqualTo(request.toString)
      r.apiToken must beEqualTo(apiToken)
    }
  }
}
