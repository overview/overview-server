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
        val foundDocumentSet = 
          Schema.documentSets.where(d => d.query === "foo").headOption

        foundDocumentSet must beSome
        val foundJob = foundDocumentSet.get.documentSetCreationJob
        foundJob.get.state must be equalTo(0)
      }
    }.pendingUntilFixed
    
      
    "redirect to documentsets view" in new DbContext {
      val result = controllers.DocumentSetController.create() (FakeRequest().
        withFormUrlEncodedBody(("query", "foo")))

      redirectLocation(result).getOrElse("No redirect") must be equalTo("/create")
    }.pendingUntilFixed
  }

}
