package controllers

import java.util.UUID

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import models.upload.OverviewUpload
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.OK
import play.api.test.Helpers.status


class UploadControllerSpec extends Specification with Mockito {

  class TestUploadController extends UploadController {

    def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] = 
      Done(Right(mock[OverviewUpload]), Input.EOF)
  }

  "UploadController" should {
    "return OK if upload succeeds" in {
      val guid = UUID.randomUUID

      val controller = new TestUploadController
      val request: Request[OverviewUpload] = FakeRequest[OverviewUpload]("POST", "/upload", FakeHeaders(), mock[OverviewUpload], "controllers.UploadController.create")
      val result = controller.authorizedCreate(guid)(request, null)
      
      status(result)  must be equalTo(OK)
    }

  }
}
