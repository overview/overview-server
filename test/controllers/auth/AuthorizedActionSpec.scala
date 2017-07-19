package controllers.auth

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{AnyContent,BodyParsers,Request, RequestHeader, Results, Result}
import play.api.test.{FakeRequest,StubPlayBodyParsersFactory}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await,Future}

import models.{Session, User}
import models.OverviewUser

class AuthorizedActionSpec extends test.helpers.InAppSpecification with Mockito {
  trait BaseScope extends Scope with StubPlayBodyParsersFactory {
    val mockSessionFactory = mock[SessionFactory]
    val authority = mock[Authority]
    val request: RequestHeader = FakeRequest()

    var calledRequest: Option[RequestHeader] = None
    def block[A](r: AuthorizedRequest[A]) : Result = {
      calledRequest = Some(r)
      mock[Result]
    }

    lazy val actionBuilderFactory = new AuthorizedAction(
      mockSessionFactory,
      new BodyParsers.Default(stubPlayBodyParsers),
      new test.helpers.MockMessagesApi(),
      materializer.executionContext
    )

    lazy val actionBuilder = actionBuilderFactory(authority)
    lazy val action = actionBuilder(block(_))

    def run = Await.result(action(request).run, Duration.Inf)
  }

  "should call the body directly when given an AuthorizedRequest" in new BaseScope {
    // Unit tests need to short-circuit the database. That's how they become
    // unit-y.
    override val request = mock[AuthorizedRequest[AnyContent]]

    action(request)

    calledRequest must beSome(request)
    there was no(mockSessionFactory).loadAuthorizedSession(request, authority)
  }

  "should return a plainResult if sessionFactory gives it" in new BaseScope {
    val result = Results.Unauthorized("foo")
    mockSessionFactory.loadAuthorizedSession(any[Request[_]], any[Authority]) returns Future.successful(Left(result))

    run must beEqualTo(result)

    calledRequest must beNone
  }

  "should invoke the block if sessionFactory gives a Right" in new BaseScope {
    val sessionAndUser = (mock[Session], mock[User])
    mockSessionFactory.loadAuthorizedSession(any[Request[_]], any[Authority]) returns Future.successful(Right(sessionAndUser))

    run

    calledRequest must beSome.like { case r: AuthorizedRequest[_] =>
      // request does weird type stuff, such that request != request. Hence toString.
      r.request.toString must beEqualTo(request.toString)
      r.userSession must beEqualTo(sessionAndUser._1)
      r.user must beEqualTo(sessionAndUser._2)
    }
  }
}
