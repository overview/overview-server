/*
 * DocumentSetControllerSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */
package controllers

import play.api.Play.{start, stop}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers.redirectLocation
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import controllers.auth.AuthorizedRequest
import controllers.forms.DocumentSetForm.Credentials
import models.OverviewUser
import models.orm.DocumentSet

class DocumentSetControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))
  
  class TestDocumentSetController extends DocumentSetController {
    var savedDocumentSet: Option[DocumentSet] = None
    var createdJobOwnerId: Option[Long] = None
    
    protected def saveDocumentSet(documentSet: DocumentSet): DocumentSet = {
      savedDocumentSet = Some(documentSet.copy(id = 1l))
      savedDocumentSet.get
    }
    protected def setDocumentSetOwner(documentSet: DocumentSet, ownerId: Long) {}
    protected def createDocumentSetCreationJob(documentSet: DocumentSet, credentials: Credentials) {
      createdJobOwnerId = Some(documentSet.id)
    }
  }
  
  trait AuthorizedSession extends Scope {
    val query = "documentSet query"
    val title = "documentSet title"
    val controller = new TestDocumentSetController
    val user = mock[OverviewUser]
    val request = new AuthorizedRequest(
      FakeRequest()
        .withSession("AUTH_USER_ID" -> user.id.toString)
        .withFormUrlEncodedBody("query" -> query, "title" -> title),
      user)

  }

  "The DocumentSet Controller" should {
    
    "submit a DocumentSetCreationJob when a new query is received" in new AuthorizedSession {
      val result = controller.create()(request)
      controller.savedDocumentSet must beSome
      val documentSet = controller.savedDocumentSet.get
      documentSet.query must beSome
      documentSet.query.get must be equalTo(query) 
      documentSet.title must be equalTo(title)
      
      controller.createdJobOwnerId must beSome
      controller.createdJobOwnerId.get must be equalTo(documentSet.id)
    }

    "redirect to documentsets view" in new AuthorizedSession {
      val result = controller.create()(request)
      redirectLocation(result).getOrElse("No redirect") must be equalTo("/documentsets")
    }
  }
  
  step(stop)
}
