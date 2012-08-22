package models.orm

import helpers.DbTestContext
import models.orm._
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }



class DocumentSetSpec extends Specification {

  step(start(FakeApplication()))
  
  "DocumentSet" should {
    
    // Need inExample because Squeryl messes up implicit conversion
    inExample("create a DocumentSetCreationJob") in new DbTestContext {
      val query = "query"

      val documentSet = new DocumentSet(query)
      Schema.documentSets.insert(documentSet)
      
      val job = documentSet.createDocumentSetCreationJob
      
      job.documentSet.head must be equalTo(documentSet)
      
      val maybeJob = Schema.documentSetCreationJobs.headOption
      maybeJob must beSome
      
      val maybeSet = Schema.documentSets.where(ds => ds.query === query).headOption
      maybeSet must beSome
      val set = maybeSet.get
      
      set.documentSetCreationJob must be equalTo(maybeJob)
    }
    
    inExample("throw exception if job creation is attempted before db insertion") in 
    	new DbTestContext {
      val query = "query"
      val documentSet = new DocumentSet("query")
      
      documentSet.createDocumentSetCreationJob() must throwAn[IllegalArgumentException]
    }
  }
  
  step(stop)
}