package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.mvc.{AnyContent, Request, RequestHeader, PlainResult}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers.{BAD_REQUEST, OK, SEE_OTHER, contentAsString, status, session}

import controllers.auth.OptionallyAuthorizedRequest
import helpers.DbTestContext
import mailers.Mailer
import models.{OverviewUser, ResetPasswordRequest}

class PasswordControllerSpec extends Specification {
  step(start(FakeApplication()))

  trait OurScope extends Scope with Mockito {
    // We need DbTestContext for the "implicit Connection", even though we never use it
    trait UserWithRequest extends OverviewUser with ResetPasswordRequest

    // We have a mock "user" and "userWithRequest" and they return each other
    val user = mock[OverviewUser]
    user.email returns "user@example.org"
    user.passwordMatches("hash") returns true
    user.withLoginRecorded(anyString, any[java.util.Date]) returns user
    user.save returns user

    val userWithRequest = mock[UserWithRequest]
    userWithRequest.email returns user.email
    userWithRequest.resetPasswordToken returns "0123456789abcd"
    userWithRequest.withNewPassword(anyString) returns user
    userWithRequest.save returns userWithRequest

    user.withResetPasswordRequest returns userWithRequest

    class OurPasswordController extends PasswordController {
      var lastMail : Option[Mailer] = None
      var loggedIn : Boolean = false

      def emailToUser(email: String): Option[OverviewUser] = {
        if (email == user.email) Some(user) else None
      }

      def tokenToUser(token: String): Option[OverviewUser with ResetPasswordRequest] = {
        if (token == userWithRequest.resetPasswordToken) Some(userWithRequest) else None
      }

      def sendMail(mail: Mailer) : Unit = {
        lastMail = Some(mail)
      }
    }

    val controller = new OurPasswordController()
    val formParameters : Option[Seq[(String,String)]] = None
    val fakeConnection : java.sql.Connection = null // TODO remove this parameter from TransactionActionController

    def requestWithUser(user: Option[OverviewUser]) = {
      new OptionallyAuthorizedRequest(FakeRequest(), user)
    }

    implicit lazy val formRequest : Request[AnyContent] = {
      val fakeRequest = formParameters.map(FakeRequest().withFormUrlEncodedBody(_:_*)).getOrElse(FakeRequest())
      new OptionallyAuthorizedRequest(fakeRequest, None)
    }
  }

  "PasswordController" should {
    "new() should redirect from the 'new' page when logged in" in new OurScope {
      val result = controller.new_()(requestWithUser(Some(OverviewUser(models.orm.User()))))
      status(result) must equalTo(SEE_OTHER)
    }

    "edit() should redirect from the 'edit' page when logged in" in new OurScope {
      val result = controller.edit("invalid-token")(requestWithUser(Some(OverviewUser(models.orm.User()))))
      status(result) must equalTo(SEE_OTHER)
    }

    "new() show the 'new' page when not logged in" in new OurScope {
      val result = controller.new_()(requestWithUser(None))
      status(result) must equalTo(OK)
    }

    "create() should return BadRequest and show a form when the user does not enter a valid email address" in new OurScope {
      override val formParameters = Some(Seq("email" -> "."))
      val result = controller.doCreate()(formRequest, fakeConnection)
      status(result) must equalTo(BAD_REQUEST)
      contentAsString(result) must contain("<form")
    }

    "create() should email a non-user when the email address is not found" in new OurScope {
      override val formParameters = Some(Seq("email" -> "invalid@example.org"))
      controller.doCreate()(formRequest, fakeConnection)
      controller.lastMail must beSome.which({ mail: Mailer =>
        mail.subject must beEqualTo("Overview Project password reset attempted")
      })
    }

    "create() should redirect when emailing a non-user" in new OurScope {
      override val formParameters = Some(Seq("email" -> "invalid@example.org"))
      val result = controller.doCreate()(formRequest, fakeConnection)
      status(result) must beEqualTo(SEE_OTHER)
    }

    "create() should call withResetPasswordRequest and save on a user" in new OurScope {
      override val formParameters = Some(Seq("email" -> user.email))
      controller.doCreate()(formRequest, fakeConnection)
      got {
        one(user).withResetPasswordRequest
        one(userWithRequest).save
      }
    }

    "create() should email a user" in new OurScope {
      override val formParameters = Some(Seq("email" -> user.email))
      controller.doCreate()(formRequest, fakeConnection)
      controller.lastMail must beSome.which({ mail: Mailer =>
        mail.subject must beEqualTo("Overview Project password reset")
      })
    }

    "create() should redirect when emailing a user" in new OurScope {
      override val formParameters = Some(Seq("email" -> user.email))
      val result = controller.doCreate()(formRequest, fakeConnection)
      status(result) must beEqualTo(SEE_OTHER)
    }

    "edit() should show the edit page" in new OurScope {
      val result = controller.edit(userWithRequest.resetPasswordToken)(requestWithUser(None))
      status(result) must beEqualTo(OK)
      contentAsString(result) must contain("<form")
    }

    "edit() should show an 'invalid token' page" in new OurScope {
      val result = controller.edit("bad-token")(requestWithUser(None))
      status(result) must beEqualTo(BAD_REQUEST)
    }

    "update() should show an 'invalid token' page" in new OurScope {
      override val formParameters = Some(Seq("password" -> "good-password"))
      val result = controller.doUpdate("bad-token")(formRequest, fakeConnection)
      status(result) must beEqualTo(BAD_REQUEST)
    }

    "update() should show an error and form when given a bad password" in new OurScope {
      override val formParameters = Some(Seq("password" -> ""))
      val result = controller.doUpdate(userWithRequest.resetPasswordToken)(formRequest, fakeConnection)
      status(result) must beEqualTo(BAD_REQUEST)
      contentAsString(result) must contain("<form")
    }

    "update() should call withNewPassword and save" in new OurScope {
      val greatPassword = "alksjgh3D~;"
      override val formParameters = Some(Seq("password" -> greatPassword))
      val result = controller.doUpdate(userWithRequest.resetPasswordToken)(formRequest, fakeConnection)
      got {
        one(userWithRequest).withNewPassword(greatPassword)
        one(user).save
      }
    }

    "update() should log the user in" in new OurScope {
      override val formParameters = Some(Seq("password" -> "aklsd@23k;"))
      val result = controller.doUpdate(userWithRequest.resetPasswordToken)(formRequest, fakeConnection)
      got {
        one(user).withLoginRecorded(anyString, any[java.util.Date])
      }
      session(result).get("AUTH_USER_ID") must beSome
    }

    "update() should redirect on success" in new OurScope {
      override val formParameters = Some(Seq("password" -> "aklsd@23k;"))
      val result = controller.doUpdate(userWithRequest.resetPasswordToken)(formRequest, fakeConnection)
      status(result) must beEqualTo(SEE_OTHER)
    }
  }

  step(stop)
}
