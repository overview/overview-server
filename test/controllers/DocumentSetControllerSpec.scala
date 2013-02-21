/*
 * DocumentSetControllerSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */
package controllers

import play.api.Play.{ start, stop }
import play.api.test.{ FakeApplication, FakeRequest }
import play.api.test.Helpers.redirectLocation
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import controllers.auth.AuthorizedRequest
import controllers.forms.DocumentSetForm.Credentials
import models.OverviewUser
import models.orm.DocumentSet
import models.orm.DocumentSetType._
import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.test.Helpers._

class DocumentSetControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  class TestDocumentSetController extends DocumentSetController {
    var savedDocumentSet: Option[DocumentSet] = None
    var createdJobOwnerId: Option[Long] = None
    
    private var documentSets: Map[Long, DocumentSet] = Map((1l, DocumentSet(DocumentCloudDocumentSet, 1l, "title", Some("query"))))
    
    protected def loadDocumentSet(id: Long): Option[DocumentSet] = {
      documentSets.get(id)
    }
    protected def saveDocumentSet(documentSet: DocumentSet): DocumentSet = {
      savedDocumentSet = Some(documentSet.copy(id = 1l))
      savedDocumentSet.get
    }
    protected def setDocumentSetOwner(documentSet: DocumentSet, ownerEmail: String) {}
    protected def createDocumentSetCreationJob(documentSet: DocumentSet, credentials: Credentials) {
      createdJobOwnerId = Some(documentSet.id)
    }
  }

  trait AuthorizedSession extends Scope {
    val controller = new TestDocumentSetController
    val user = mock[OverviewUser]
    def request = new AuthorizedRequest(
      FakeRequest()
        .withSession("AUTH_USER_ID" -> user.id.toString)
        .withFormUrlEncodedBody(sessionForm: _*), user)

    def sessionForm: Seq[(String, String)]
  }

  trait CreateRequest extends AuthorizedSession {
    val query = "documentSet query"
    val title = "documentSet title"
    override def sessionForm = Seq("query" -> query, "title" -> title)
  }

  trait UpdateRequest extends AuthorizedSession {
    val documentSetId: Long = 1l
    val newTitle = "New Title"
    override def sessionForm = Seq("public" -> "true", "title" -> newTitle)
  }
  
  trait BadUpdateRequest extends AuthorizedSession {
    val documentSetId: Long = 1l
    override def sessionForm = Seq("public" -> "not a boolean")
  }

  "The DocumentSet Controller" should {

    "submit a DocumentSetCreationJob when a new query is received" in new CreateRequest {
      val result = controller.create()(request)
      controller.savedDocumentSet must beSome
      val documentSet = controller.savedDocumentSet.get
      documentSet.query must beSome
      documentSet.query.get must be equalTo (query)
      documentSet.title must be equalTo (title)

      controller.createdJobOwnerId must beSome
      controller.createdJobOwnerId.get must be equalTo (documentSet.id)
    }

    "redirect to documentsets view" in new CreateRequest {
      val result = controller.create()(request)
      redirectLocation(result).getOrElse("No redirect") must be equalTo ("/documentsets")
    }
    
    "update the DocumentSet" in new UpdateRequest {
      val result = controller.update(documentSetId)(request)
      
      status(result) must be equalTo(OK)
      controller.savedDocumentSet must beSome
      val documentSet = controller.savedDocumentSet.get
      
      documentSet.isPublic must beTrue
      documentSet.title must be equalTo(newTitle)
    }
    
    "return NotFound if document set is bad" in new UpdateRequest {
      val result = controller.update(-1l)(request)
      status(result) must be equalTo(NOT_FOUND)
    }
    
    "return BadRequest if form input is bad" in new BadUpdateRequest {
      val result = controller.update(documentSetId)(request)
      status(result) must be equalTo(BAD_REQUEST)
    }
  }

  step(stop)
}
