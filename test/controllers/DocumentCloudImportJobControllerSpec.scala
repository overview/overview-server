package controllers

import org.specs2.specification.Scope
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.DocumentSetBackend
import models.DocumentCloudImportJob
import org.overviewproject.models.DocumentSet
import org.overviewproject.test.factories.{PodoFactory=>factory}

class DocumentCloudImportJobControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockDocumentSetBackend = smartMock[DocumentSetBackend]
    val mockStorage = smartMock[DocumentCloudImportJobController.Storage]
    val controller = new DocumentCloudImportJobController {
      override val documentSetBackend = mockDocumentSetBackend
      override val storage = mockStorage
    }
  }

  trait CreateScope extends BaseScope {
    mockDocumentSetBackend.create(any, any) returns Future.successful(factory.documentSet(id=123L))
    mockStorage.insertJob(any, any) returns Future.successful(())
    def formBody = Seq("title" -> "title", "query" -> "projectid:1-slug", "lang" -> "en")
    def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody : _*)
    lazy val result = controller.create()(request)
  }

  "DocumentCloudImportJobController" should {
    "submit a DocumentSetCreationJob" in new CreateScope {
      h.status(result) must beEqualTo(h.SEE_OTHER)
      there was one(mockDocumentSetBackend).create(
        beLike[DocumentSet.CreateAttributes] { case attributes =>
          attributes.title must beEqualTo("title")
          attributes.query must beSome("projectid:1-slug")
        },
        beLike[String] { case s => s must beEqualTo(request.user.email) }
      )
      there was one(mockStorage).insertJob(
        123L,
        DocumentCloudImportJob(request.user.email, "title", "projectid:1-slug", "en", None, false, "", "")
      )
    }

    "redirect to /documentsets" in new CreateScope {
      h.redirectLocation(result) must beSome("/documentsets")
    }

    "not submit an invalid job" in new CreateScope {
      override def formBody = Seq()
      h.status(result) must beEqualTo(h.BAD_REQUEST)
      there was no(mockDocumentSetBackend).create(any, any)
    }
  }
}
