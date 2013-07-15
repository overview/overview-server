package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{ start, stop }
import play.api.mvc.{ AnyContent, Request }
import play.api.test.{ FakeApplication, FakeRequest }
import play.api.test.Helpers._

import org.overviewproject.tree.Ownership
import controllers.auth.AuthorizedRequest
import models.DocumentCloudImportJob
import models.OverviewUser

class DocumentCloudImportJobControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  trait BaseScope extends Scope {
    val mockStorage = mock[DocumentCloudImportJobController.Storage]
    val controller = new DocumentCloudImportJobController {
      override val storage = mockStorage
    }
    val user = mock[OverviewUser].smart
    def fakeRequest: Request[AnyContent] = FakeRequest()
    def request = new AuthorizedRequest(fakeRequest, user)
  }

  trait CreateScope extends BaseScope {
    def formBody = Seq("title" -> "title", "query" -> "projectid:1-slug", "lang" -> "en")
    override def fakeRequest = FakeRequest().withFormUrlEncodedBody(formBody : _*)
    lazy val result = controller.create()(request)
  }

  "DocumentCloudImportJobController" should {
    "submit a DocumentSetCreationJob" in new CreateScope {
      result
      there was one(mockStorage).insertJob(any[DocumentCloudImportJob])
    }

    "redirect to /documentsets" in new CreateScope {
      redirectLocation(result) must beSome("/documentsets")
    }

    "not submit an invalid job" in new CreateScope {
      override def formBody = Seq()
      status(result) must beEqualTo(BAD_REQUEST)
    }
  }

  step(stop)
}
