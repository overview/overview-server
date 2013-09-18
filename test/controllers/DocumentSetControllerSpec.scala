/*
 * DocumentSetControllerSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */
package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{ start, stop }
import play.api.test.{ FakeApplication, FakeRequest }
import play.api.test.Helpers._

import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.DocumentSet
import controllers.auth.AuthorizedRequest
import controllers.forms.DocumentSetForm.Credentials
import org.overviewproject.tree.orm.DocumentSetUser
import models.{ OverviewUser, ResultPage }

class DocumentSetControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  trait BaseScope extends Scope {
    val mockStorage = mock[DocumentSetController.Storage]
    val controller = new DocumentSetController {
      override val storage = mockStorage
    }
    mockStorage.insertOrUpdateDocumentSet(any[DocumentSet]) returns DocumentSet(id=1L)
    val user = mock[OverviewUser].smart
  }

  trait AuthorizedSession extends BaseScope {
    def request = new AuthorizedRequest(
      FakeRequest()
        .withSession("AUTH_USER_ID" -> user.id.toString)
        .withFormUrlEncodedBody(sessionForm: _*), user)

    def sessionForm: Seq[(String, String)]
  }

  class UpdateRequest extends AuthorizedSession {
    val documentSetId: Long = 1l
    val newTitle = "New Title"
    override def sessionForm = Seq("public" -> "true", "title" -> newTitle)
  }

  class BadUpdateRequest extends AuthorizedSession {
    val documentSetId: Long = 1l
    mockStorage.findDocumentSet(anyLong) returns Some(DocumentSet(id=1L))
    override def sessionForm = Seq("public" -> "not a boolean")
  }

  "The DocumentSet Controller" should {
    "update the DocumentSet" in new UpdateRequest {
      mockStorage.findDocumentSet(anyLong) returns Some(DocumentSet(id=1L))
      val result = controller.update(documentSetId)(request)
      there was one(mockStorage).insertOrUpdateDocumentSet(any)
    }

    "return NotFound if document set is bad" in new UpdateRequest {
      mockStorage.findDocumentSet(anyLong) returns None
      val result = controller.update(-1l)(request)
      status(result) must be equalTo (NOT_FOUND)
    }

    "return BadRequest if form input is bad" in new BadUpdateRequest {
      val result = controller.update(documentSetId)(request)
      status(result) must be equalTo (BAD_REQUEST)
    }
  }

  step(stop)
}
