package models.orm

import helpers.DbTestContext
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import ua.t3hnar.bcrypt._

class UserSpec extends Specification {
  
  trait UserContext extends DbTestContext {
	val query = "query"
	val email = "foo@bar.net"
	val rawPassword = "raw password"
	val hashedPassword = rawPassword.bcrypt(7)
	val user = new User(email, hashedPassword)
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
    
    "prepare new registration with hashed password" in new UserContext {
      val newUser = User.prepareNewRegistration(email, rawPassword)
      rawPassword.isBcrypted(newUser.passwordHash) must beTrue
    }
  }
  
  step(stop)

}