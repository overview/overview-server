/*
 * DocumentSetControllerSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */
package controllers

import helpers.DbTestContext
import models.orm.{DocumentSet, DocumentSetCreationJob, Schema, User}
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.mvc.Controller
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._
import play.api.Play.{start, stop}



class DocumentSetControllerSpec extends Specification {
  step(start(FakeApplication()))
  
  class TestDocumentSetController() extends Controller with AuthorizedDocumentSetController;  
  
  trait AuthorizedSession extends DbTestContext {
    val query = "documentSet query"
    val title = "documentSet title"
    implicit val authorizedRequest = 
      FakeRequest().withFormUrlEncodedBody("query" -> query, "title" -> title)
    val controller = new TestDocumentSetController()
    lazy val user = 
      Schema.users.where(u => u.email === "admin@overview-project.org").head
    lazy val documentSet = Schema.documentSets.headOption
    lazy val documentSetCreationJob = Schema.documentSetCreationJobs.headOption
  }

  
  "The DocumentSet Controller" should {
    
    inExample("submit a DocumentSetCreationJob when a new query is received") in new AuthorizedSession {
      val result = controller.authorizedCreate(user)
      documentSet must beSome
      documentSetCreationJob must beSome
      documentSet.get.query must beSome equalTo(Some(query))
      documentSet.get.title must be equalTo(title)
      documentSetCreationJob.get.documentSetId must be equalTo(documentSet.get.id)
    }
    
      
    inExample("redirect to documentsets view") in new AuthorizedSession {
      val result = controller.authorizedCreate(user)
      redirectLocation(result).getOrElse("No redirect") must be equalTo("/documentsets")
    }
  }
  
  step(stop)
}
