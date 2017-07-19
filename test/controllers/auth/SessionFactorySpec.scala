package controllers.auth

import java.util.UUID
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{Result, RequestHeader}
import play.api.test.{FakeRequest, DefaultAwaitTimeout}
import scala.concurrent.Future

import controllers.backend.{SessionBackend,UserBackend}
import models.{Session, User}

class SessionFactorySpec extends test.helpers.InAppSpecification with Mockito with JsonMatchers with DefaultAwaitTimeout {
  val h = play.api.test.Helpers

  trait BaseScope extends Scope {
    val authority = smartMock[Authority]
    val authConfig = smartMock[AuthConfig]
    authConfig.isMultiUser returns true
    val mockSessionBackend = smartMock[SessionBackend]
    val mockUserBackend = smartMock[UserBackend]

    lazy val factory = new SessionFactory(
      authConfig,
      mockSessionBackend,
      mockUserBackend,
      materializer.executionContext
    )

    case class PossibleSession(id: UUID, user: Option[User]) {
      def sessionAndUser = user.map((u) => (Session(u.id, "127.0.0.1").copy(id=id), u))
    }

    val unauthenticatedSession = PossibleSession(UUID.randomUUID(), None)
    val authenticatedSession = PossibleSession(UUID.randomUUID(), Some(User(2L, "user2@example.org")))
    val authorizedSession = PossibleSession(UUID.randomUUID(), Some(User(3L, "user3@example.org")))

    mockSessionBackend.showWithUser(unauthenticatedSession.id) returns Future.successful(None)
    mockSessionBackend.showWithUser(authenticatedSession.id) returns Future.successful(authenticatedSession.sessionAndUser)
    mockSessionBackend.showWithUser(authorizedSession.id) returns Future.successful(authorizedSession.sessionAndUser)

    authority.apply(authenticatedSession.user.get) returns Future.successful(false)
    authority.apply(authorizedSession.user.get) returns Future.successful(true)

    def await[A](f: => Future[A]): A = scala.concurrent.Await.result(f, scala.concurrent.duration.Duration.Inf)

    def sessionId: UUID = UUID.randomUUID()
    def sessionIdString: String = sessionId.toString
    def sessionData: Seq[(String,String)] = Seq(SessionFactory.SessionIdKey -> sessionIdString)
    def request: RequestHeader = FakeRequest().withSession(sessionData: _*)
    def resultFuture: Future[Either[Result,(Session,User)]] = factory.loadAuthorizedSession(request, authority)
    def result: Either[Result,(Session,User)] = await(resultFuture)
  }

  "SessionFactory" should {
    "redirect when session is empty and request is not xhr" in new BaseScope {
      override def sessionData = Seq()
      result must beLeft.like({ case r: Result => r.header.status must beEqualTo(h.SEE_OTHER) })
    }
    
    "return 400 on xhr when session is empty" in new BaseScope {
      override def request = FakeRequest().withHeaders("X-Requested-With" -> "Adam")
      result must beLeft.like({ case r: Result =>
        r.header.status must beEqualTo(h.BAD_REQUEST)
        val json = h.contentAsString(Future.successful(r))
        json must /("code" -> "unauthenticated")
      })
    }

    "redirect when session ID is not a UUID" in new BaseScope {
      override def sessionIdString = "32414-adsf"
      result must beLeft.like({ case r: Result => r.header.status must beEqualTo(h.SEE_OTHER) })
    }

    "redirect when the session ID is a UUID that is not found" in new BaseScope {
      override def sessionId = unauthenticatedSession.id
      result must beLeft.like({ case r: Result => r.header.status must beEqualTo(h.SEE_OTHER) })
    }

    "return Forbidden when the user is not authorized" in new BaseScope {
      override def sessionId = authenticatedSession.id
      result must beLeft.like({ case r: Result => r.header.status must beEqualTo(h.FORBIDDEN) })
      there was one(authority).apply(authenticatedSession.user.get)
    }

    "return a Right when user is authorized" in new BaseScope {
      override def sessionId = authorizedSession.id
      result must beRight.like({ case (s: Session, u: User) => u.id must beEqualTo(authorizedSession.user.get.id) })
      there was one(authority).apply(authorizedSession.user.get)
    }
  }
}
