package models.orm

import anorm._
import anorm.SqlParser._
import helpers.DbTestContext
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }
import ua.t3hnar.bcrypt._

class UserSpec extends Specification {
  
  trait UserContext extends DbTestContext {
	val query = "query"
	val email = "foo@bar.net"
	val rawPassword = "raw password"
	val hashedPassword = "pretend hash"
  }
  
  step(start(FakeApplication()))
  
  "User" should {
    
    "Create a DocumentSet" in new DbTestContext {
      val query = "query"
      val email = "foo@bar.net"
      val password = "pencil69"
      		
      val user = new User(email, password)
      Schema.users.insert(user)
      
      val documentSet = user.createDocumentSet(query)
      
      documentSet.users must haveTheSameElementsAs(Seq(user))

      val maybeResult = SQL("SELECT document_set_id, user_id FROM document_set_user").
          as(long("document_set_id") ~ long("user_id") map(flatten) singleOpt)
          
      maybeResult must beSome
      val (documentSetId, userId) = maybeResult.get
      
      documentSetId must be equalTo(documentSet.id)
      userId must be equalTo(user.id)
    }
    
    "Not create a DocumentSet if not inserted in database" in new DbTestContext {
      val query = "query"
      val email = "foo@bar.net"
      val password = "pencil69"
      		
      val user = new User(email, password)
            
      user.createDocumentSet(query) must throwAn[IllegalArgumentException]
    }
    
    "prepare new registration with hashed password" in new UserContext {
      val user = User.prepareNewRegistration(email, rawPassword)
      rawPassword.isBcrypted(user.passwordHash) must beTrue
    }
  }
  
  step(stop)

}