package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._

import controllers.auth.AuthorizedRequest
import models.{OverviewDocument,OverviewUser}

class DocumentControllerSpec extends Specification {
  step(start(FakeApplication()))

  class TestDocumentController(val document: OverviewDocument) extends DocumentController {
    override def findDocumentById(documentId: Long) = Some(document).filter(_.id == documentId)
  }

  trait DocumentScope extends Scope with Mockito {
    val validDocumentId = 1L
    val invalidDocumentId = 99L
    val description = "Description"
    val url = "https://example.org/1"

    val document = mock[OverviewDocument.DocumentCloudDocument]
    document.id returns validDocumentId
    document.description returns description
    document.title returns None
    document.titleOrDescription returns description
    document.url(anyString) returns url

    val user = mock[OverviewUser]
    val request = new AuthorizedRequest(FakeRequest(), user)
    val requestedDocumentId : Long

    val controller = new TestDocumentController(document)

    lazy val result = controller.show(requestedDocumentId)(request)
  }

  trait ValidDocumentScope extends DocumentScope {
    override val requestedDocumentId = validDocumentId
  }

  trait InvalidDocumentScope extends DocumentScope {
    override val requestedDocumentId = invalidDocumentId
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
