package models.orm

import helpers.DbTestContext
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import ua.t3hnar.bcrypt._

class UserSpec extends Specification {
  
  trait UserInfo {
	val query = "query"
	val email = "foo@bar.net"
	val rawPassword = "raw password"
	val hashedPassword = rawPassword.bcrypt(7)
  }
  
  trait UserContext extends DbTestContext with UserInfo {
  	val user = new User(email, hashedPassword)
  }
  
  trait RegistrationContext extends DbTestContext with UserInfo {
    val user = User.prepareNewRegistration(email, rawPassword)  
  }
  
  step(start(FakeApplication()))
  
  "User" should {
    
    inExample("Create a DocumentSet") in new UserContext {
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
  }
  
  step(stop)

}