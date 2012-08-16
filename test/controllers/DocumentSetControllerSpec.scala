package controllers

import org.specs2.mutable._
import org.specs2.specification._
import org.squeryl.PrimitiveTypeMode._
import play.api.test._
import play.api.test.Helpers._

import models.orm.{DocumentSetCreationJob, Schema}
import helpers.DbContext

class DocumentSetControllerSpec extends Specification {
  
  "The DocumentSet Controller" should {
    inExample("submit a DocumentSetCreationJob when a new query is received")in new DbContext {
      val result = controllers.DocumentSetController.create() (FakeRequest().
        withFormUrlEncodedBody(("query", "foo")))	
      inTransaction {
        val foundJob = 
          Schema.documentSetCreationJobs.where(d => d.query === "foo").headOption

        foundJob must beSome
        foundJob.get.query must beEqualTo("foo")
      }.pendingUntilFixed
    }
    
      
    "redirect to documentsets view" in new DbContext {
      val result = controllers.DocumentSetController.create() (FakeRequest().
        withFormUrlEncodedBody(("query", "foo")))

      redirectLocation(result).getOrElse("No redirect") must be equalTo("/create")
    }.pendingUntilFixed
  }

}
