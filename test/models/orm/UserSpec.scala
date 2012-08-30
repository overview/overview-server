package models.orm

import helpers.DbTestContext
import java.sql.Timestamp
import org.joda.time.DateTime.now
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import ua.t3hnar.bcrypt._

class UserSpec extends Specification {
  
  trait UserInfo extends DbTestContext {
	val query = "query"
	val email = "foo@bar.net"
	val rawPassword = "raw password"
	val hashedPassword = rawPassword.bcrypt(7)
  }
  
  trait UserContext extends UserInfo {
  	val user = User(email = email, passwordHash = hashedPassword)
  }
  
  trait RegistrationContext extends UserInfo {
    val user = User.prepareNewRegistration(email, rawPassword)  
  }
  
  trait AdminUser extends DbTestContext {
    val adminUser = "admin@overview-project.org"
    val adminPassword = "admin@overview-project.org"
  }
  
  step(start(FakeApplication()))
  
  "User" should {
    
    "Create a DocumentSet" in new UserContext {
      Schema.users.insert(user)
      
      val documentSet = user.createDocumentSet(query)
      
      documentSet.users must haveTheSameElementsAs(Seq(user))
      
      val documentSetUserLink = Schema.documentSetUsers.where(dsu => 
        dsu.documentSetId === documentSet.id and dsu.userId === user.id
      ).headOption
      
      documentSetUserLink must beSome
    }
    
    "Not create a DocumentSet if not inserted in database" in new UserContext {
      user.createDocumentSet(query) must throwAn[IllegalArgumentException]
    }

    
    "create user with hashed password" in new UserInfo {
      val user = User(email, rawPassword)
      rawPassword.isBcrypted(user.passwordHash) must beTrue
    }
    
    "prepare new registration with hashed password" in new RegistrationContext {
      rawPassword.isBcrypted(user.passwordHash) must beTrue
    }
    
    inExample("have a long alphanumeric confirmation token") in new RegistrationContext {

      user.confirmationToken must beSome.like { case s => 
        s must be matching("""[a-zA-Z0-9]{26}""") 
      }
    } 
    
    inExample("save confirmation token") in new RegistrationContext {
      user.save
      val savedUser = User.findById(user.id)
      savedUser must beSome.like {case u => u must be equalTo(user)}
    }
    
    "throw an exception if confirmation token is not unique" in new UserInfo {
      val token = Some("a token")
      val user1 = User(0, email, hashedPassword, token)
      val user2 = User(0, "user2", "password".bcrypt(7), token)
      
      user1.save
      user2.save must throwA[java.lang.RuntimeException]
    }.pendingUntilFixed
    
    inExample("have a confirmation sent at date") in new RegistrationContext {
      val currentTime = now().getMillis()
      user.confirmationSentAt must beSome.like {case t => 
        t.getTime must be closeTo(currentTime, 1000)}
    }
    
    
    "check valid credentials" in new AdminUser {
      User(adminUser, adminPassword).hasValidCredentials must beTrue
    }
    
    "checks wrong password" in new AdminUser {
      User(adminUser, adminPassword + "not!").hasValidCredentials must beFalse
    }
    
    "check wrong email" in new UserInfo {
      User(email, rawPassword).hasValidCredentials must beFalse
    }

    "check registration not confirmed" in new RegistrationContext {
      user.save
      User(email, rawPassword).isConfirmed must beFalse
    }
    
    "check confirmed registration" in new RegistrationContext {
      val confirmedUser = user.withConfirmation
      confirmedUser.save

      User(email, rawPassword).isConfirmed must beTrue
      User(email, rawPassword).confirmationToken must beNone
    }
    
    inExample("find user by confirmation token") in new RegistrationContext {
      user.save
      val registeredUser = User.findByConfirmationToken(user.confirmationToken.get)
      
      registeredUser must beSome.like { case u =>
        u.email must be equalTo(user.email) 
      }
    }
  }
  
  step(stop)

}