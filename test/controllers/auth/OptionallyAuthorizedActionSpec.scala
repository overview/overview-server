package controllers.auth

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{AnyContent, Request, RequestHeader, Results, SimpleResult}
import play.api.test.FakeRequest
import scala.concurrent.duration.Duration
import scala.concurrent.Await

import models.orm.{Session, User}
import models.OverviewUser

class OptionallyAuthorizedActionSpec extends test.InAppSpecification with Mockito {
  trait BaseScope extends Scope {
    val mockSessionFactory = mock[SessionFactory]
    val authority = mock[Authority]
    val request: RequestHeader = FakeRequest()

    var calledRequest: Option[RequestHeader] = None
    def block[A](r: OptionallyAuthorizedRequest[A]) : SimpleResult = {
      calledRequest = Some(r)
      mock[SimpleResult]
    }

    lazy val actionBuilderFactory = new OptionallyAuthorizedAction {
      override protected val sessionFactory = mockSessionFactory
    }

    lazy val actionBuilder = actionBuilderFactory(authority)
    lazy val action = actionBuilder(block(_))

    def run = Await.result(action(request).run, Duration.Inf)
  }

  "should call the body directly when given an OptionallyAuthorizedRequest" in new BaseScope {
    // Unit tests need to short-circuit the database. That's how they become
    // unit-y.
    override val request = mock[OptionallyAuthorizedRequest[AnyContent]]

    action(request)

    calledRequest must beSome(request)
    there was no(mockSessionFactory).loadAuthorizedSession(request, authority)
  }

  "should invoke the block if sessionFactory gives a Right" in new BaseScope {
    val sessionAndUser = (mock[Session], mock[User])
    mockSessionFactory.loadAuthorizedSession(any[Request[_]], any[Authority]) returns Right(sessionAndUser)

    run

    calledRequest must beSome.like { case r: OptionallyAuthorizedRequest[_] =>
      // request does weird type stuff, such that request != request. Hence toString.
      r.request.toString must beEqualTo(request.toString)
      r.userSession must beSome(sessionAndUser._1)
      r.user.map(_.toUser) must beSome(sessionAndUser._2)
    }
  }
}
