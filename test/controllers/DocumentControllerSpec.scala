package controllers

import org.specs2.specification.Scope
import play.api.mvc.AnyContent

import controllers.auth.AuthorizedRequest
import models.OverviewDocument
import org.overviewproject.tree.orm.Document

class DocumentControllerSpec extends ControllerSpecification {
  trait DocumentScope extends Scope {
    val mockStorage = mock[DocumentController.Storage]

    val controller = new DocumentController {
      override val storage = mockStorage
    }

    val request : AuthorizedRequest[AnyContent] = fakeAuthorizedRequest

    val requestedDocumentId = 1L
    lazy val result = controller.show(requestedDocumentId)(request)
  }

  trait ValidDocumentScope extends DocumentScope {
    val document = mock[OverviewDocument]
    document.id returns requestedDocumentId
    document.description returns "description"
    document.title returns None
    document.text returns None
    document.suppliedId returns None
    document.url returns None

    mockStorage.find(anyInt) returns Some(document)
  }

  trait InvalidDocumentScope extends DocumentScope {
    mockStorage.find(anyInt) returns None
  }

  "DocumentController" should {
    "show() should return NotFound when ID is invalid" in new InvalidDocumentScope {
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }

    "show() should return Ok when ID is valid" in new ValidDocumentScope {
      h.status(result) must beEqualTo(h.OK)
    }
  }
}
