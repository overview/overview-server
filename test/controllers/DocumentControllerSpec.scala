package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._

import controllers.auth.AuthorizedRequest
import models.{OverviewDocument,OverviewUser}
import org.overviewproject.tree.orm.Document

class DocumentControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  trait DocumentScope extends Scope {
    val mockStorage = mock[DocumentController.Storage]

    val controller = new DocumentController {
      override val storage = mockStorage
    }

    val user = mock[OverviewUser]
    val request = new AuthorizedRequest(FakeRequest(), user)

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
      status(result) must beEqualTo(NOT_FOUND)
    }

    "show() should return Ok when ID is valid" in new ValidDocumentScope {
      status(result) must beEqualTo(OK)
    }
  }

  step(stop)
}
