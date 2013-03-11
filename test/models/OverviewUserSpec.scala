package models

import java.sql.Timestamp
import org.joda.time.DateTime.now
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import ua.t3hnar.bcrypt._

import helpers.DbTestContext

class OverviewUserSpec  extends Specification {
  
  trait NewRegistration extends DbTestContext {
    val email = "user@example.org"
    val password = "password"
    lazy val user = PotentialUser(email, password).requestConfirmation
  }

  trait ExistingUserContext extends DbTestContext {
    val id = 1l
    val email = "admin@overview-project.org"
    val password = "admin@overview-project.org"
  }

  trait LoadedUserContext extends ExistingUserContext {
    lazy val user : OverviewUser = OverviewUser.findById(id).get
  }
    
  trait LoadedUserWithResetRequestContext extends ExistingUserContext {
    lazy val user : OverviewUser with ResetPasswordRequest = OverviewUser.findById(id).get.withResetPasswordRequest
  }

  step(start(FakeApplication()))
  
  "OverviewUser" should {
    "find user by id" in new ExistingUserContext {
      OverviewUser.findById(id) must beSome.like {case u =>
        u.email must be equalTo(email)
        } 
    }
    
    "find user by email" in new ExistingUserContext {
      OverviewUser.findByEmail(email) must beSome.like {case u =>
        u.passwordMatches(password) must beTrue  
      }
    }
    
    "find user by email insensitive to case" in new ExistingUserContext {
      OverviewUser.findByEmail(email.toUpperCase) must beSome.like {case u =>
        u.passwordMatches(password) must beTrue  
      }
    }
    
    "find user by email insensitive to case of stored value" in new DbTestContext {
      val email = "MixedCase@UPPERCASE.NET"
      val password = "password"
      val user = PotentialUser(email, password).requestConfirmation.confirm
      user.save
      
      OverviewUser.findByEmail(email) must beSome.like { case u =>
        u.email must be equalTo(email)
      }
    }
   
    "find user by confirmation token" in new NewRegistration {
      user.save
      val tokenUser = OverviewUser.findByConfirmationToken(user.confirmationToken)
      tokenUser must beSome.like {case u => 
        u.email must be equalTo(user.email)
        u.confirmationToken must be equalTo(user.confirmationToken)  
      }
    }
    
    "return none if confirmation token not found" in new DbTestContext {
      OverviewUser.findByConfirmationToken("not a real token") must beNone
    }

    "record login data in withLoginRecorded()" in new LoadedUserContext {
      val user2 = user.withLoginRecorded("1.1.1.1", new java.util.Date(5000))
      user2.currentSignInAt must beSome(new java.sql.Timestamp(5000))
      user2.currentSignInIp must beSome("1.1.1.1")
    }

    "store previous withLoginRecorded() data when calling withLoginRecorded()" in new LoadedUserContext {
      val user2 = user.withLoginRecorded("1.1.1.1", new java.util.Date(5000)) // relies on previous test passing
      val user3 = user2.withLoginRecorded("2.2.2.2", new java.util.Date(6000))
      user3.lastSignInAt must beSome(new java.sql.Timestamp(5000))
      user3.lastSignInIp must beSome("1.1.1.1")
    }

    "record login activity in withActivityRecorded()" in new LoadedUserContext {
      val user2 = user.withActivityRecorded("1.1.1.1", new java.util.Date(5000))
      user2.lastActivityAt must beSome(new java.sql.Timestamp(5000))
      user2.lastActivityIp must beSome("1.1.1.1")
    }

    "generate a reset-password token" in new LoadedUserContext {
      val user2 = user.withResetPasswordRequest
      user2.resetPasswordToken must_!= ""
      user2.resetPasswordSentAt.getTime must_!= 0
    }

    "reset the user's password" in new LoadedUserWithResetRequestContext {
      val user2 = user.withNewPassword("great new password")
      user2.passwordMatches("great new password") must beTrue
      val ormUser = models.orm.User.findById(user2.id).get
      ormUser.resetPasswordToken must beNone
      ormUser.resetPasswordSentAt must beNone
    }
    
    "set subscription status" in new LoadedUserContext {
      user.isSubscribedToEmail must beFalse
      val user2 = user.withEmailSubscription(true)
      user2.isSubscribedToEmail must beTrue
    }
  }

  "OverviewUser with ConfirmationRequest" should {
    "create a new user for registration" in new NewRegistration {
      user.email must be equalTo(email)
      user.passwordMatches(password) must beTrue
      user.passwordMatches(password + "not!") must beFalse
    }
    
    "new user has confirmation token sent around now" in new NewRegistration {
      user.confirmationToken must be matching("""[a-zA-Z0-9]{26}""")
      user.confirmationSentAt.getTime must be closeTo(now().getMillis(), 500)
    }
    
    "confirm request" in new NewRegistration {
      user.save
      val confirmedUser = user.confirm
      
      confirmedUser.withConfirmationRequest must beNone
    }
    
    "remove confirmation token when confirmed" in new NewRegistration {
      val token = user.confirmationToken
      user.save
      
      user.confirm
      user.save
      
      user.withConfirmationRequest must beNone
    }
    
    "remember unconfirmed state after confirmation" in new NewRegistration {
      val token = user.confirmationToken
      user.save
      
      user.confirm
      user.save
      user.confirmationToken must be equalTo(token)
    }
  }
  
  "PotentialUser" should {
    
    "validate email and password" in new ExistingUserContext {
      val user = PotentialUser(email, password)
      
      user.withValidCredentials must beSome.like { case u =>
        u.id must not be equalTo(0l)
      }
    }
    
    "not validate with bad email" in new ExistingUserContext {
      val user = PotentialUser(email + "not!", password)
      
      user.withValidCredentials must beNone
    }
    
    "not validate with bad password" in new ExistingUserContext {
      val user = PotentialUser(email, password + "not!")
      
      user.withValidCredentials must beNone
    }
    
    "return an existing user without validating" in new ExistingUserContext {
      val user = PotentialUser(email, password + "not!")
      
      user.withRegisteredEmail must beSome.like { case u => u.email must be equalTo(user.email) }
    }
    
    "return None if user doesn't exist" in new NewRegistration {
      PotentialUser(email, password).withRegisteredEmail must beNone
    }
    
    "get user with confirmation request" in new NewRegistration {
      user.save
      PotentialUser(email, password).withConfirmationRequest must beSome.like {case u =>
        u.email must be equalTo(email)
      }
    }
    
    "get user with confirmation request disregarding password" in new NewRegistration {
      user.save
      PotentialUser(email, "").withConfirmationRequest must beSome.like {case u =>
        u.email must be equalTo(email)
      }
    }  
    
    "return None if user has no confirmation request" in new ExistingUserContext {
      val user = PotentialUser(email, password)
      
      user.withConfirmationRequest must beNone
    }
  }
  
  
  step(stop)
}
