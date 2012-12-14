/*
 * DocumentSetControllerSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */
package controllers

import org.specs2.mutable.Specification
import org.squeryl.PrimitiveTypeMode._
import play.api.mvc.{AnyContent, Controller}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._
import play.api.Play.{start, stop}

import org.overviewproject.tree.orm.DocumentSetCreationJob
import controllers.auth.AuthorizedRequest
import helpers.DbTestContext
import models.orm.{DocumentSet, Schema, User}
import models.OverviewUser

class DocumentSetControllerSpec extends Specification {
  step(start(FakeApplication()))
  
  class TestDocumentSetController extends DocumentSetController
  
  trait AuthorizedSession extends DbTestContext {
    val query = "documentSet query"
    val title = "documentSet title"
    val controller = new TestDocumentSetController
    lazy val ormUser = Schema.users.where(u => u.email === "admin@overview-project.org").head
    lazy val user = OverviewUser(ormUser).save
    lazy val request = new AuthorizedRequest(
      FakeRequest()
        .withSession("AUTH_USER_ID" -> user.id.toString)
        .withFormUrlEncodedBody("query" -> query, "title" -> title),
      user)
    lazy val documentSet = from(Schema.documentSets)(ds => select(ds)).headOption
    lazy val documentSetCreationJob = from(Schema.documentSetCreationJobs)(dscj => select(dscj)).headOption
  }

  "The DocumentSet Controller" should {
    
    inExample("submit a DocumentSetCreationJob when a new query is received") in new AuthorizedSession {
      val result = controller.create()(request)
      documentSet must beSome
      documentSetCreationJob must beSome
      documentSet.get.query must beSome.like { case q => q must be equalTo(query) }
      documentSet.get.title must be equalTo(title)
      documentSetCreationJob.get.documentSetId must be equalTo(documentSet.get.id)
    }
    
      
    inExample("redirect to documentsets view") in new AuthorizedSession {
      val result = controller.create()(request)
      redirectLocation(result).getOrElse("No redirect") must be equalTo("/documentsets")
    }
  }
  
  step(stop)
}
