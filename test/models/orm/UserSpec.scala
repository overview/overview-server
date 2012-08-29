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
  	val user = new User(email, hashedPassword)
  }
  
  trait RegistrationContext extends UserInfo {
    val user = User.prepareNewRegistration(email, rawPassword)  
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
      val user1 = new User(email, hashedPassword, token)
      val user2 = new User("user2", "password".bcrypt(7), token)
      
      user1.save
      user2.save must throwA[java.lang.RuntimeException]
    }
    
    inExample("have a confirmation sent at date") in new RegistrationContext {
      val currentTime = now().getMillis()
      user.confirmationSentAt must beSome.like {case t => 
        t.getTime must be closeTo(currentTime, 1000)}
    }
    
  }
  
  step(stop)

}