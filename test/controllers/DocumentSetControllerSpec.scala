/*
 * DocumentSetControllerSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */
package controllers

import helpers.DbTestContext
import models.orm.{DocumentSet, DocumentSetCreationJob, Schema}
import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._
import play.api.Play.{start, stop}


class DocumentSetControllerSpec extends Specification {
  step(start(FakeApplication()))
  
  trait AuthorizedSession extends DbTestContext {
    lazy val login = controllers.SessionController.create() (FakeRequest().
      withFormUrlEncodedBody(("email", "admin@overview-project.org"),
                             ("password", "admin@overview-project.org")))
                               
    lazy val sessionCookie = cookies(login)("PLAY_SESSION")
  }
  
  "The DocumentSet Controller" should {
    
    "submit a DocumentSetCreationJob when a new query is received" in new AuthorizedSession {
      val authorizedRequest = FakeRequest().withFormUrlEncodedBody(("query", "foo")).
      	        withCookies(sessionCookie)

      val result = controllers.DocumentSetController.create()(authorizedRequest)
      val maybeDocumentSet = Schema.documentSets.headOption
      maybeDocumentSet must beSome
      val documentSet = maybeDocumentSet.get

      val foundJob = 
        Schema.documentSetCreationJobs.
          where(d => d.documentSetId === documentSet.id).headOption

      foundJob must beSome
      foundJob.get.state must beEqualTo(DocumentSetCreationJob.State.NotStarted)
      
      controllers.DocumentSetController.delete(documentSet.id)(authorizedRequest)
    }
    
      
    inExample("redirect to documentsets view") in new AuthorizedSession {
      val authorizedRequest = FakeRequest().withFormUrlEncodedBody(("query", "foo")).
      	        withCookies(sessionCookie)

      val result = controllers.DocumentSetController.create()(authorizedRequest)
      val documentSet = Schema.documentSets.head
      
      redirectLocation(result).getOrElse("No redirect") must be equalTo("/documentsets")
      controllers.DocumentSetController.delete(documentSet.id)(authorizedRequest)
    }
  }
  
  step(stop)
}
