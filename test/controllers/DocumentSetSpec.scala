package controllers

import org.specs2.mutable._
import org.specs2.specification._

import play.api.test._
import play.api.test.Helpers._

import models.DocumentSetCreationJob

class DocumentSetSpec extends Specification {
  
  "The DocumentSet Controller" should {
    "submit a DocumentSetCreationJob when a new query is received" in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
        val result = controllers.DocumentSet.createDocumentSet() (FakeRequest().
            withFormUrlEncodedBody(("query", "foo")))

            
        val foundJob = DocumentSetCreationJob.find.where().eq("query", "foo").findUnique
        
        foundJob must not beNull; // without this semicolon the next line gets gobbled up 
        foundJob.query must beEqualTo("foo")

      }
    }
    
      
    "redirect to documentsets view" in {
      running(FakeApplication(additionalConfiguration = inMemoryDatabase())) {
    	  val result = controllers.DocumentSet.createDocumentSet() (FakeRequest().
            withFormUrlEncodedBody(("query", "foo")))
      
          redirectLocation(result).getOrElse("No redirect") must be equalTo("/documentsets")
      }
    }
  }

}