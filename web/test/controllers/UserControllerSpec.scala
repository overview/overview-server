package controllers

import org.specs2.specification.Scope
import play.api.Configuration
import play.api.mvc.{AnyContent,Request,RequestHeader}

import com.overviewdocs.database.exceptions.Conflict
import models.{PotentialNewUser,User}

class UserControllerSpec extends ControllerSpecification {
  trait OurScope extends Scope {
    val mockBackendStuff = smartMock[UserController.BackendStuff]
    val controller = new UserController(
      Configuration("overview.contact_url" -> "https://contact.us", "overview.allow_registration" -> true),
      mockBackendStuff,
      fakeMessagesActionBuilder,
      fakeControllerComponents,
      mockView[views.html.Session._new]
    )
    mockBackendStuff.createUser(any[PotentialNewUser]) answers(_ match {
      case u: PotentialNewUser => {
        User(
          email=u.email,
          passwordHash=User.hashPassword(u.password),
          confirmationToken=Some("confirmation-token")
        )
      }
      case _ => null
    })
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
        there was one(mockBackendStuff).mailExistingUser(any, any)(any)
      }

      "create and email a new user" in new CreateScope {
        mockBackendStuff.findUserByEmail("user@example.org") returns None
        h.status(response) must beEqualTo(h.SEE_OTHER)
        there was one(mockBackendStuff).createUser(PotentialNewUser("user@example.org", "abcdefg", true))
        there was one(mockBackendStuff).mailNewUser(any, any, any)(any)
      }

      "just redirect on unique-key violation" in new CreateScope {
        mockBackendStuff.findUserByEmail("user@example.org") returns None
        mockBackendStuff.createUser(any) answers { _ => throw new Conflict(null) }
        h.status(response) must beEqualTo(h.SEE_OTHER)
        there was no(mockBackendStuff).mailExistingUser(any, any)(any)
        there was no(mockBackendStuff).mailNewUser(any, any, any)(any)
      }
    }
  }
}
