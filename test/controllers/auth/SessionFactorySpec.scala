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
    val mockIsMultiUser : Boolean

    val authority = mock[Authority]
    val mockStorage = mock[SessionFactory.Storage]

    val factory = new SessionFactory {
      override protected val storage = mockStorage
      override protected lazy val isMultiUser = mockIsMultiUser
    }

    def sessionId : UUID = UUID.randomUUID()
    def sessionIdString = sessionId.toString
    def sessionData : Seq[(String,String)] = Seq(SessionFactory.SessionIdKey -> sessionIdString)
    def request : RequestHeader = FakeRequest().withSession(sessionData: _*)
    def result : Either[SimpleResult, (Session,User)] = factory.loadAuthorizedSession(request, authority)
  }

  "SessionFactory" should {
    "in multi-user mode" should {
      trait MultiUserScope extends BaseScope {
        override lazy val mockIsMultiUser = true

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
      }

      "redirect when session is empty" in new MultiUserScope {
        override def sessionData = Seq()
        result must beLeft.like({ case r: SimpleResult => r.header.status must beEqualTo(h.SEE_OTHER) })
      }

      "redirect when session ID is not a UUID" in new MultiUserScope {
        override def sessionIdString = "32414-adsf"
        result must beLeft.like({ case r: SimpleResult => r.header.status must beEqualTo(h.SEE_OTHER) })
      }

      "redirect when the session ID is a UUID that is not found" in new MultiUserScope {
        override def sessionId = unauthenticatedSession.id
        result must beLeft.like({ case r: SimpleResult => r.header.status must beEqualTo(h.SEE_OTHER) })
      }

      "return Forbidden when the user is not authorized" in new MultiUserScope {
        override def sessionId = authenticatedSession.id
        result must beLeft.like({ case r: SimpleResult => r.header.status must beEqualTo(h.FORBIDDEN) })
        there was one(authority).apply(authenticatedSession.user.get)
      }

      "return a Right when user is authorized" in new MultiUserScope {
        override def sessionId = authorizedSession.id
        result must beRight.like({ case (s: Session, u: User) => u.id must beEqualTo(authorizedSession.user.get.id) })
        there was one(authority).apply(authorizedSession.user.get)
      }
    }

    "in single-user mode" should {
      trait SingleUserScope extends BaseScope {
        override lazy val mockIsMultiUser = false
      }

      "return a Right with a new Session when there is no Session in the DB" in new SingleUserScope {
        override def sessionData = Seq()
        mockStorage.loadUser(1L) returns Some(User(1L, role=UserRole.Administrator))
        result must beRight.like({
          case (s: Session, u: User) => {
            u.role must beEqualTo(UserRole.NormalUser)
            s.userId must beEqualTo(1L)
            s.id must beEqualTo(new UUID(0, 1L))
            s.isPersisted must beEqualTo(true)
          }
        })
      }

      "return a Left if the User is not in the DB" in new SingleUserScope {
        mockStorage.loadUser(1L) returns None
        result must beLeft.like({ case r: SimpleResult => r.header.status must beEqualTo(h.FORBIDDEN) })
      }
    }
  }

  step(stop)
}
