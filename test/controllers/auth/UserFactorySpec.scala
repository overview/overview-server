package controllers.auth

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.mvc.{PlainResult, RequestHeader}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers.{FORBIDDEN, SEE_OTHER, status}

import models.OverviewUser

class UserFactorySpec extends Specification {
  step(start(FakeApplication()))

  trait MultiUserScope extends Scope with Mockito {
    val authorizedUserId = 1L
    val unauthorizedUserId = 2L
    val nonexistentUserId = 3L

    val UserIdKey = UserFactory.UserIdKey

    def createMockUser(id: Long) : OverviewUser = {
      val user = mock[OverviewUser]
      user.id returns id
      user.withActivityRecorded(anyString, any[java.util.Date]) returns user
      user.asNormalUser returns user
      user.save returns user
      user
    }

    val authorizedUser = createMockUser(authorizedUserId)
    val unauthorizedUser = createMockUser(unauthorizedUserId)

    val authority = new Authority {
      def apply(user: OverviewUser) = (user.id == authorizedUserId)
    }

    val request: RequestHeader

    val userFactory = new UserFactory {
      override protected def userIdToUser(id: Long) = {
        if (id == 1L) Some(authorizedUser)
        else if (id == 2L) Some(unauthorizedUser)
        else None
      }

      override protected lazy val isMultiUser = true
    }

    def result: Either[PlainResult, OverviewUser] = userFactory.loadUser(request, authority)
  }

  trait AuthorizedMultiUserScope extends MultiUserScope {
    override val request = FakeRequest().withSession(UserIdKey -> authorizedUserId.toString)
  }

  trait SingleUserScope extends MultiUserScope {
    override val request = FakeRequest()
    override val userFactory = new UserFactory {
      override protected def userIdToUser(id: Long) = {
        if (id == 1L) Some(authorizedUser)
        else if (id == 2L) Some(unauthorizedUser)
        else None
      }

      override protected lazy val isMultiUser = false
    }
  }

  "UserFactory" should {
    "loadUser() should return Redirect when session is empty" in new MultiUserScope() {
      override val request = FakeRequest()
      result must beLeft.like({case r: PlainResult => status(r) must beEqualTo(SEE_OTHER)})
    }

    "loadUser() should return Redirect when the session does not contain a user-id value" in new MultiUserScope() {
      override val request = FakeRequest().withSession("SomeOtherKey" -> "SomeValueWeIgnore")
      result must beLeft.like({case r: PlainResult => status(r) must beEqualTo(SEE_OTHER)})
    }

    "loadUser() should return Redirect when the user-id is not an integer" in new MultiUserScope() {
      override val request = FakeRequest().withSession(UserIdKey -> "123... oops")
      result must beLeft.like({case r: PlainResult => status(r) must beEqualTo(SEE_OTHER)})
    }

    "loadUser() should return Redirect when the user-id is not a valid user" in new MultiUserScope() {
      override val request = FakeRequest().withSession(UserIdKey -> nonexistentUserId.toString)
      result must beLeft.like({case r: PlainResult => status(r) must beEqualTo(SEE_OTHER)})
    }

    "loadUser() should return Forbidden when the user is not authorized" in new MultiUserScope() {
      override val request = FakeRequest().withSession(UserIdKey -> unauthorizedUserId.toString)
      result must beLeft.like({case r: PlainResult => status(r) must beEqualTo(FORBIDDEN)})
    }

    "loadUser() should return an OverviewUser when the user is authorized" in new AuthorizedMultiUserScope() {
      result must beRight.like({case u: OverviewUser => u.id must beEqualTo(authorizedUserId)})
    }

    "loadUser() should record activity" in new AuthorizedMultiUserScope() {
      result
      there was one(authorizedUser).withActivityRecorded(anyString, any[java.util.Date])
      there was one(authorizedUser).save
    }

    "loadUser() should not call asNormalUser() in multi-user mode" in new AuthorizedMultiUserScope() {
      result
      there was no(authorizedUser).asNormalUser
    }

    "loadUser() should always return an OverviewUser when in single-user mode" in new SingleUserScope() {
      result must beRight.like({case u: OverviewUser => u.id must beEqualTo(authorizedUserId)})
    }

    "loadUser() should call asNormalUser() in single-user mode" in new SingleUserScope() {
      result
      there was one(authorizedUser).asNormalUser
      there was one(authorizedUser).save
    }
  }

  step(stop)
}
