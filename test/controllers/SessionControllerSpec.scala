package controllers

import org.specs2.specification.Scope
import play.api.mvc.AnyContent

import controllers.auth.OptionallyAuthorizedRequest
import models.{OverviewUser,Session}

class SessionControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = mock[SessionController.Storage]

    val controller = new SessionController {
      override val storage = mockStorage
    }
  }

  "SessionController" should {
    "new_" should {
      trait NewScope extends BaseScope {
        def maybeUser : Option[OverviewUser] = Some(fakeUser)
        def request = fakeOptionallyAuthorizedRequest(maybeUser)
        lazy val result = controller.new_()(request)
      }

      "redirect when user is already logged in" in new NewScope {
        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "show a form when user is not logged in" in new NewScope {
        override def maybeUser = None
        h.status(result) must beEqualTo(h.OK)
      }
    }

    "delete" should {
      trait DeleteScope extends BaseScope {
        def maybeUser : Option[OverviewUser] = Some(fakeUser)
        def request = fakeOptionallyAuthorizedRequest(maybeUser)
        lazy val result = controller.delete()(request)
      }

      "redirect when the user is already logged out and has no cookie" in new DeleteScope {
        override def maybeUser = None
        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "redirect when the user is already logged out and has a cookie" in new DeleteScope {
        val requestWithUser = super.request
        override def request = fakeOptionallyAuthorizedRequest(None).withSession("AUTH_SESSION_ID" -> requestWithUser.userSession.get.id.toString)
        h.status(result) must beEqualTo(h.SEE_OTHER)
        h.session(result).isEmpty must beTrue
      }

      "log out when the user is not logged out" in new DeleteScope {
        val requestWithUser = super.request
        override def request = requestWithUser.withSession("AUTH_SESSION_ID" -> requestWithUser.userSession.get.id.toString)
        h.status(result) must beEqualTo(h.SEE_OTHER)
        h.session(result).isEmpty must beTrue
        there was one(mockStorage).deleteSession(any[Session])
      }
    }

    "create" should {
      // We have DB logic in our Form (and our Form is what does the actual
      // authentication), so dependency injection is a lost cause. We'll save
      // this for integration tests -- it should be easy to catch errors.
    }
  }
}
