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
      
      val documentSet = user.createDocumentSet(query)
      val job = documentSet.createDocumentSetCreationJob()
      
      val maybeUpdatedUser = Schema.users.where(u => u.email === adminUser).headOption
      
      maybeUpdatedUser must beSome
      val updatedUser = maybeUpdatedUser.get
      
      val foundDocumentSet = 
        updatedUser.documentSets.where(ds => ds.id === documentSet.id).headOption.get
      
     foundDocumentSet.documentSetCreationJob must beNone
     
     val docSetWithJobLoaded = foundDocumentSet.withCreationJob
     
     docSetWithJobLoaded.documentSetCreationJob must beSome
     val foundJob = docSetWithJobLoaded.documentSetCreationJob.get
     
     foundJob.id must be equalTo(job.id)
    }
  }
  
  step(stop)
}
