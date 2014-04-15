package controllers

import org.specs2.specification.Scope
import play.api.mvc.AnyContent

import org.overviewproject.tree.Ownership
import controllers.auth.AuthorizedRequest
import models.DocumentCloudImportJob

class DocumentCloudImportJobControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = mock[DocumentCloudImportJobController.Storage]
    val controller = new DocumentCloudImportJobController {
      override val storage = mockStorage
    }
  }

  trait CreateScope extends BaseScope {
    def formBody = Seq("title" -> "title", "query" -> "projectid:1-slug", "lang" -> "en")
    def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody : _*)
    lazy val result = controller.create()(request)
  }

  "DocumentCloudImportJobController" should {
    "submit a DocumentSetCreationJob" in new CreateScope {
      result
      there was one(mockStorage).insertJob(any[DocumentCloudImportJob])
    }

    "redirect to /documentsets" in new CreateScope {
      h.redirectLocation(result) must beSome("/documentsets")
    }

    "not submit an invalid job" in new CreateScope {
      override def formBody = Seq()
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }
  }
}
