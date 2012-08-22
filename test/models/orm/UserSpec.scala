package models.orm

import anorm._
import anorm.SqlParser._
import helpers.DbTestContext
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }


class UserSpec extends Specification {
  
  step(start(FakeApplication()))
  
  "User" should {
    
    inExample("Create a DocumentSet") in new DbTestContext {
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
    
    inExample("Not create a DocumentSet if not inserted in database") in new DbTestContext {
      val query = "query"
      val email = "foo@bar.net"
      val password = "pencil69"
      		
      val user = new User(email, password)
            
      user.createDocumentSet(query) must throwAn[IllegalArgumentException]
    }
  }
  
  step(stop)

}