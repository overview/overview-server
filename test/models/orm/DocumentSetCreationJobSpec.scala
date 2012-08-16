package models.orm

import helpers.DbTestContext
import models.orm._
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }



class DocumentSetCreationJobSpec extends Specification {

  step(start(FakeApplication()))
  
  "DocumentSetCreationJob" should {
    
    // Need inExample because Squeryl messes up implicit conversion
    inExample("save query and user to database") in new DbTestContext {
      val adminUser = "admin@overview-project.org"
      val query = "query"
        
      val maybeUser = Schema.users.where(u => u.email === adminUser).headOption
      
      maybeUser must beSome
      val user = maybeUser.get
      
      val job = new DocumentSetCreationJob(query, user.id)
      user.documentSetCreationJobs.associate(job)
      
      user.documentSetCreationJobs must contain(job)
      
      val maybeUpdatedUser = Schema.users.where(u => u.email === adminUser).headOption
      
      maybeUpdatedUser must beSome
      val updatedUser = maybeUpdatedUser.get
      
      updatedUser.documentSetCreationJobs must haveTheSameElementsAs(Seq(job))
      
    }
  }
  
  step(stop)
}