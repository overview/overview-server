package controllers.auth

import java.util.UUID
import play.api.mvc.{SimpleResult, RequestHeader}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.Play.{start,stop}

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import models.OverviewUser
import models.orm.{Session, User, UserRole}

class SessionFactorySpec extends Specification with Mockito {
  step(start(FakeApplication())) // to load application.secret

  val h = play.api.test.Helpers

  trait BaseScope extends Scope {
    val authority = mock[Authority]
    val mockStorage = mock[SessionFactory.Storage]

    val factory = new SessionFactory {
      override protected val storage = mockStorage
    }

    case class PossibleSession(id: UUID, user: Option[OverviewUser]) {
      def sessionAndUser = user.map((u) => (Session(u.id, "127.0.0.1").copy(id=id), u.toUser))
    }

    val unauthenticatedSession = PossibleSession(UUID.randomUUID(), None)
    val authenticatedSession = PossibleSession(UUID.randomUUID(), Some(OverviewUser(User(2L, "user2@example.org"))))
    val authorizedSession = PossibleSession(UUID.randomUUID(), Some(OverviewUser(User(3L, "user3@example.org"))))

    mockStorage.loadSessionAndUser(unauthenticatedSession.id) returns unauthenticatedSession.sessionAndUser
    mockStorage.loadSessionAndUser(authenticatedSession.id) returns authenticatedSession.sessionAndUser
    mockStorage.loadSessionAndUser(authorizedSession.id) returns authorizedSession.sessionAndUser

    authority.apply(authenticatedSession.user.get) returns false
    authority.apply(authorizedSession.user.get) returns true

    def sessionId : UUID = UUID.randomUUID()
    def sessionIdString = sessionId.toString
    def sessionData : Seq[(String,String)] = Seq(SessionFactory.SessionIdKey -> sessionIdString)
    def request : RequestHeader = FakeRequest().withSession(sessionData: _*)
    def result : Either[SimpleResult, (Session,User)] = factory.loadAuthorizedSession(request, authority)
  }

  "SessionFactory" should {
    "redirect when session is empty" in new BaseScope {
      override def sessionData = Seq()
      result must beLeft.like({ case r: SimpleResult => r.header.status must beEqualTo(h.SEE_OTHER) })
    }

    "redirect when session ID is not a UUID" in new BaseScope {
      override def sessionIdString = "32414-adsf"
      result must beLeft.like({ case r: SimpleResult => r.header.status must beEqualTo(h.SEE_OTHER) })
    }

    "redirect when the session ID is a UUID that is not found" in new BaseScope {
      override def sessionId = unauthenticatedSession.id
      result must beLeft.like({ case r: SimpleResult => r.header.status must beEqualTo(h.SEE_OTHER) })
    }

    "return Forbidden when the user is not authorized" in new BaseScope {
      override def sessionId = authenticatedSession.id
      result must beLeft.like({ case r: SimpleResult => r.header.status must beEqualTo(h.FORBIDDEN) })
      there was one(authority).apply(authenticatedSession.user.get)
    }

    "return a Right when user is authorized" in new BaseScope {
      override def sessionId = authorizedSession.id
      result must beRight.like({ case (s: Session, u: User) => u.id must beEqualTo(authorizedSession.user.get.id) })
      there was one(authority).apply(authorizedSession.user.get)
    }
  }

  step(stop)
}
