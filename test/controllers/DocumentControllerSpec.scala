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
  }

  trait ShowScope {
    self: DocumentScope =>

    lazy val result = controller.show(requestedDocumentId)(request)
  }

  trait ShowTextScope extends DocumentScope {
    self: DocumentScope =>

    lazy val result = controller.showText(requestedDocumentId)(request)
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
    "show()" should {
      "return NotFound when ID is invalid" in new ShowScope with InvalidDocumentScope {
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return Ok when ID is valid" in new ShowScope with ValidDocumentScope {
        h.status(result) must beEqualTo(h.OK)
      }
    }

    "showText()" should {
      "return NotFound when ID is invalid" in new ShowTextScope with InvalidDocumentScope {
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return the empty string when the ID is valid and there is no text" in new ShowTextScope with ValidDocumentScope {
        document.text returns None
        h.status(result) must beEqualTo(h.OK)
        h.contentType(result) must beSome("text/plain")
        h.charset(result) must beSome("utf-8")
        h.contentAsString(result) must beEqualTo("")
      }

      "return the text when the ID is valid" in new ShowTextScope with ValidDocumentScope {
        document.text returns Some("foo")
        h.status(result) must beEqualTo(h.OK)
        h.contentType(result) must beSome("text/plain")
        h.charset(result) must beSome("utf-8")
        h.contentAsString(result) must beEqualTo("foo")
      }
    }
  }
}
