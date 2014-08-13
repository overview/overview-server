package controllers.auth

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{AnyContent, Request, RequestHeader, Results, Result}
import play.api.test.FakeRequest
import scala.concurrent.duration.Duration
import scala.concurrent.Await

import models.{Session, User}
import models.OverviewUser

class AuthorizedActionSpec extends test.InAppSpecification with Mockito {
  trait BaseScope extends Scope {
    val mockSessionFactory = mock[SessionFactory]
    val authority = mock[Authority]
    val request: RequestHeader = FakeRequest()

    var calledRequest: Option[RequestHeader] = None
    def block[A](r: AuthorizedRequest[A]) : Result = {
      calledRequest = Some(r)
      mock[Result]
    }

    var loggedActivity: Option[(RequestHeader, Session, User)] = None
    lazy val actionBuilderFactory = new AuthorizedAction {
      override protected val sessionFactory = mockSessionFactory
      override protected def logActivity(request: RequestHeader, session: Session, user: User) = {
        loggedActivity = Some((request, session, user))
        (session, user)
      }
    }

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
    loggedActivity must beNone
  }

  "should return a plainResult if sessionFactory gives it" in new BaseScope {
    val result = Results.Unauthorized("foo")
    mockSessionFactory.loadAuthorizedSession(any[Request[_]], any[Authority]) returns Left(result)

    run must beEqualTo(result)

    calledRequest must beNone
    loggedActivity must beNone
  }

  "should log activity if the sesionFactory gives a Right" in new BaseScope {
    val sessionAndUser = (mock[Session], mock[User])
    mockSessionFactory.loadAuthorizedSession(any[Request[_]], any[Authority]) returns Right(sessionAndUser)

    run

    // request does weird type stuff, such that request != request. Hence toString.
    loggedActivity.map(_.toString) must beSome((request.asInstanceOf[Request[AnyContent]], sessionAndUser._1, sessionAndUser._2).toString)
  }

  "should invoke the block if sessionFactory gives a Right" in new BaseScope {
    val sessionAndUser = (mock[Session], mock[User])
    mockSessionFactory.loadAuthorizedSession(any[Request[_]], any[Authority]) returns Right(sessionAndUser)

    run

    calledRequest must beSome.like { case r: AuthorizedRequest[_] =>
      // request does weird type stuff, such that request != request. Hence toString.
      r.request.toString must beEqualTo(request.toString)
      r.userSession must beEqualTo(sessionAndUser._1)
      r.user.toUser must beEqualTo(sessionAndUser._2)
    }
  }
}
