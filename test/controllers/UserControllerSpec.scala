package controllers

import akka.util.Timeout
import java.sql.SQLException
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import org.squeryl.SquerylSQLException
import play.api.Play.{start,stop}
import play.api.data.Form
import play.api.mvc.{AnyContent,Request,RequestHeader}
import play.api.test.{FakeApplication,FakeRequest}
import play.api.test.Helpers.{SEE_OTHER, status}
import org.overviewproject.test.Specification
import controllers.forms.UserForm
import mailers.Mailer
import models.{PotentialNewUser, OverviewUser, ConfirmationRequest}

class UserControllerSpec extends Specification {
  step(start(FakeApplication()))

  implicit val timeout : Timeout = Timeout(999999, scala.concurrent.duration.MILLISECONDS)

  trait OurScope extends Scope with Mockito {
    val validEmail = "user@example.org"
    val validPassword = "askdfjg#$@r5djD"

    // XXX remove vars
    var existingUserMailed = false
    var newUserMailed = false
    var savedUser : Option[OverviewUser] = None
    
    trait TestUserController extends UserController {
      override def mailExistingUser(user: OverviewUser)(implicit request: RequestHeader) = {
        existingUserMailed = true
      }

      override def mailNewUser(user: OverviewUser with ConfirmationRequest)(implicit request: RequestHeader) = {
        newUserMailed = true
      }

      override protected def saveUser(user: OverviewUser with ConfirmationRequest) : OverviewUser with ConfirmationRequest = {
        savedUser = Some(user)
        user
      }
    }

    val controller : TestUserController
    val requestHeader : Request[AnyContent]
    def run = controller.create()(requestHeader)
  }

  trait OurScopeWithInvalidFormContents extends OurScope {
    object TestUserControllerImpl extends TestUserController
    override val controller : TestUserController = TestUserControllerImpl
    override val requestHeader = FakeRequest().withFormUrlEncodedBody(
      "email" -> "bademail",
      "password" -> validPassword
    )
  }

  trait OurScopeWithUser extends OurScope {
    val optionalOverviewUser : Option[OverviewUser]
    val subscribe: Boolean = false
    
    lazy val potentialUser = new PotentialNewUser(validEmail, validPassword, subscribe, optionalOverviewUser)

    trait TestUserControllerWithUser extends TestUserController {
      override val userForm = UserForm { (_: String, _: String, _: Boolean) => potentialUser }
    }
    object TestUserControllerWithUserImpl extends TestUserControllerWithUser

    override val controller : TestUserController = TestUserControllerWithUserImpl
    override val requestHeader = FakeRequest().withFormUrlEncodedBody(
      "email" -> validEmail,
      "password" -> validPassword
    )
  }

  trait OurScopeWithExistingUser extends OurScopeWithUser {
    val overviewUser = mock[OverviewUser]
    overviewUser.email returns validEmail
    override val optionalOverviewUser = Some(overviewUser)
  }

  trait OurScopeWithNewUser extends OurScopeWithUser {
    override val optionalOverviewUser = None
  }
  
  trait OurScopeWithUniqueKeyViolation extends OurScopeWithNewUser {
    trait TestUserControllerWithUniqueKeyViolation extends TestUserControllerWithUser {
      /*
       * Test a race:
       *
       * 1. User #1 registers, with email "test@example.org"
       * 2. User #2 registers, with email "test@example.org"
       *    (there isn't a "test@example.org" in the DB yet)
       * 3. User #1's "test@example.org" gets a confirmation token
       * 4. User #2's "test@example.org" gets a confirmation token
       * 5. User #1's "test@example.org" is saved to the DB
       * 6. User #2 tries to save, but gets a unique key violation.
       *
       * This test class mocks User #2's request.
       */

      override protected def saveUser(user: OverviewUser with ConfirmationRequest) : OverviewUser with ConfirmationRequest = {
        throw SquerylSQLException("unique key violation", new SQLException("unique key violation", "23505"))
      }
    }
    object TestUserControllerWithUniqueKeyViolationImpl extends TestUserControllerWithUniqueKeyViolation

    override val controller : TestUserController = TestUserControllerWithUniqueKeyViolationImpl
  }

  "UserController" should {
    "create() with an existing user should email the user" in new OurScopeWithExistingUser {
      existingUserMailed = false
      newUserMailed = false
      run
      existingUserMailed must beTrue
      newUserMailed must beFalse
    }

    "create() with an existing user should redirect" in new OurScopeWithExistingUser {
      val result = run
      status(result) must equalTo(SEE_OTHER)
    }
    
    "create() with a new user should email the user" in new OurScopeWithNewUser {
      existingUserMailed = false
      newUserMailed = false
      run
      existingUserMailed must beFalse
      newUserMailed must beTrue
    }

    "create() should set a new user treeTooltipsEnabled=true" in new OurScopeWithNewUser {
      savedUser = None
      run
      savedUser must beSome[OverviewUser].like { case u: OverviewUser => u.treeTooltipsEnabled must beEqualTo(true) }
    }

    "create() with a new user should redirect" in new OurScopeWithNewUser {
      val result = run
      status(result) must equalTo(SEE_OTHER)
    }

    "create() with a new user and unique-key violation should not email" in new OurScopeWithUniqueKeyViolation {
      existingUserMailed = false
      newUserMailed = false
      run
      existingUserMailed must beFalse
      newUserMailed must beFalse
    }

    "create() with a new user and unique-key violation should redirect" in new OurScopeWithUniqueKeyViolation {
      val result = run
      status(result) must equalTo(SEE_OTHER)
    }
  }

  step(stop)
}
