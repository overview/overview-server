package controllers

import java.util.Date
import org.specs2.specification.Scope
import play.api.mvc.RequestHeader
import scala.concurrent.Future

import controllers.backend.SessionBackend
import models.{Session, User}

class PasswordControllerSpec extends ControllerSpecification {
  trait OurScope extends Scope {
    // We have a mock "user" and "userWithRequest" and they return each other
    val user = User(123L, "user@example.org", User.hashPassword("hash"))
    val userWithToken = User(123L, "user@example.org", User.hashPassword("hash"), resetPasswordToken=Some("0123456789abcd"))

    val mockStorage = smartMock[PasswordController.Storage]
    val mockMail = smartMock[PasswordController.Mail]
    val mockSessionBackend = smartMock[SessionBackend]

    mockStorage.findUserByEmail(any[String]) returns None
    mockStorage.findUserByEmail("user@example.org") returns Some(user)
    mockStorage.findUserByResetToken(any[String]) returns None
    mockStorage.findUserByResetToken("0123456789abcd") returns Some(userWithToken)
    mockStorage.resetPassword(userWithToken, "Ersh3Phowb9") returns Future.successful(())
    mockSessionBackend.create(any[Long], any[String]) returns Future.successful(Session(123L, "127.0.0.1"))

    val controller = new PasswordController with TestController {
      override val sessionBackend = mockSessionBackend
      override val storage = mockStorage
      override val mail = mockMail
    }
  }

  "PasswordController" should {
    "_new()" should {
      trait NewScope extends OurScope {
        def request = fakeOptionallyAuthorizedRequest(None)
        lazy val result = controller._new()(request)
      }

      "redirect when the user is logged in" in new NewScope {
        override def request = fakeOptionallyAuthorizedRequest(Some(fakeUser))
        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "show some HTML when the user is not logged in" in new NewScope {
        h.status(result) must beEqualTo(h.OK)
      }
    }

    "edit()" should {
      trait EditScope extends OurScope {
        def request = fakeOptionallyAuthorizedRequest(None)
        val token: String = "0123456789abcd"
        lazy val result = controller.edit(token)(request)
      }

      "redirect when the user is logged in" in new EditScope {
        override def request = fakeOptionallyAuthorizedRequest(Some(fakeUser))
        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "show the edit page" in new EditScope {
        h.status(result) must beEqualTo(h.OK)
        h.contentAsString(result) must contain("<form")
      }

      "show an 'invalid token page'" in new EditScope {
        override val token = "bad-token"
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }
    }

    "create()" should {
      trait CreateScope extends OurScope {
        val params : Seq[(String,String)] = Seq()
        def request = fakeOptionallyAuthorizedRequest(None).withFormUrlEncodedBody(params: _*)
        lazy val result = controller.create()(request)
      }

      "return BadRequest and show a form when the user enters an invalid email address" in new CreateScope {
        override val params = Seq("email" -> ".")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must contain("<form")
      }

      "when the user is not found" should {
        trait CreateScopeUserNotFound extends CreateScope {
          override val params = Seq("email" -> "invalid@example.org")
          h.status(result) // run
        }

        "email the non-user" in new CreateScopeUserNotFound {
          there was one(mockMail).sendCreateErrorUserDoesNotExist(any[String])(any[RequestHeader])
        }

        "redirect" in new CreateScopeUserNotFound {
          h.status(result) must beEqualTo(h.SEE_OTHER)
        }

        "flash a message" in new CreateScopeUserNotFound {
          h.flash(result).get("success") must beSome("controllers.PasswordController.create.success,invalid@example.org")
        }

        "not change the database" in new CreateScopeUserNotFound {
          there was no(mockStorage).addResetPasswordTokenToUser(any)
        }
      }

      "when the user is found" should {
        trait CreateScopeUserFound extends CreateScope {
          mockStorage.addResetPasswordTokenToUser(user) returns "a-token"

          override val params = Seq("email" -> "user@example.org")
          h.status(result) // run
        }

        "redirect" in new CreateScopeUserFound {
          h.status(result) must beEqualTo(h.SEE_OTHER)
        }

        "flash a message" in new CreateScopeUserFound {
          h.flash(result).get("success") must beSome("controllers.PasswordController.create.success,user@example.org")
        }

        "change the database" in new CreateScopeUserFound {
          there was one(mockStorage).addResetPasswordTokenToUser(user)
        }

        "email the user" in new CreateScopeUserFound {
          there was one(mockMail).sendCreated(any)(any)
        }
      }
    }

    "update()" should {
      trait UpdateScope extends OurScope {
        val params : Seq[(String,String)] = Seq("password" -> "Ersh3Phowb9")
        val token : String = "0123456789abcd"
        def request = fakeOptionallyAuthorizedRequest(None).withFormUrlEncodedBody(params: _*)
        lazy val result = controller.update(token)(request)
      }

      "show an invalid-token page" in new UpdateScope {
        override val token = "bad-token"
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.session(result).get("AUTH_USER_ID") must beNone
      }

      "show an error and form when given a bad password" in new UpdateScope {
        override val params = Seq("password" -> "")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must contain("<form")
        h.session(result).get("AUTH_USER_ID") must beNone
      }

      "show the actual error message when given a bad password" in new UpdateScope {
        // Issue #254
        override val params = Seq("password" -> "a")
        h.contentAsString(result) must contain("""<fieldset class="control-group error""")
      }

      "save the user with a new password" in new UpdateScope {
        h.status(result)
        there was one(mockStorage).resetPassword(userWithToken, "Ersh3Phowb9")
      }

      "log the user in" in new UpdateScope {
        h.session(result).get("AUTH_SESSION_ID") must beSome
        there was one(mockSessionBackend).create(any[Long], any[String])
      }

      "redirect" in new UpdateScope {
        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "flash that the password was changed" in new UpdateScope {
        h.flash(result).get("success") must beSome("controllers.PasswordController.update.success")
      }
    }
  }
}
