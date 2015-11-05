package controllers

import org.specs2.specification.Scope
import play.api.mvc.{AnyContent,Request,RequestHeader}

import com.overviewdocs.database.exceptions.Conflict
import models.{PotentialNewUser,User}

class UserControllerSpec extends ControllerSpecification {
  trait OurScope extends Scope {
    val mockBackendStuff = smartMock[UserController.BackendStuff]

    val controller = new UserController {
      override val backendStuff = mockBackendStuff
    }
  }

  "UserController" should {
    "create" should {
      trait CreateScope extends OurScope {
        val formData: Seq[(String,String)] = Seq(
          "email" -> "user@example.org",
          "password" -> "abcdefg",
          "subscribe" -> "true"
        )

        lazy val request = fakeRequest.withFormUrlEncodedBody(formData: _*)
        lazy val response = controller.create()(request)
      }

      "email an existing user" in new CreateScope {
        mockBackendStuff.findUserByEmail("user@example.org") returns Some(User())
        h.status(response) must beEqualTo(h.SEE_OTHER)
        there was no(mockBackendStuff).createUser(any)
        there was one(mockBackendStuff).mailExistingUser(any)(any)
      }

      "create and email a new user" in new CreateScope {
        mockBackendStuff.findUserByEmail("user@example.org") returns None
        h.status(response) must beEqualTo(h.SEE_OTHER)
        there was one(mockBackendStuff).createUser(PotentialNewUser("user@example.org", "abcdefg", true))
        there was one(mockBackendStuff).mailNewUser(any)(any)
      }

      "just redirect on unique-key violation" in new CreateScope {
        mockBackendStuff.findUserByEmail("user@example.org") returns None
        mockBackendStuff.createUser(any) answers { _ => throw new Conflict(null) }
        h.status(response) must beEqualTo(h.SEE_OTHER)
        there was no(mockBackendStuff).mailExistingUser(any)(any)
        there was no(mockBackendStuff).mailNewUser(any)(any)
      }
    }
  }
}
