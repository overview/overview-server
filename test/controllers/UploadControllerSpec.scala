package controllers

import java.util.UUID

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
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
import play.api.test.Helpers.PARTIAL_CONTENT
import play.api.test.Helpers.status

@RunWith(classOf[JUnitRunner])
class UploadControllerSpec extends Specification with Mockito {

  class TestUploadController extends UploadController {

    def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] = 
      Done(Right(mock[OverviewUpload]), Input.EOF)
  }

  "UploadController" should {
    "return OK if upload is complete" in {
      val guid = UUID.randomUUID

      val controller = new TestUploadController
      val request: Request[OverviewUpload] = FakeRequest[OverviewUpload]("POST", "/upload", FakeHeaders(), mock[OverviewUpload], "controllers.UploadController.create")
      val result = controller.authorizedCreate(guid)(request, null)
      
      status(result)  must be equalTo(OK)
    }
    
    "return PARTIAL_CONTENT if upload is not complete" in {
      val upload = mock[OverviewUpload]
      upload.bytesUploaded returns 100
      upload.size returns 1000
      
      val guid = UUID.randomUUID

      val controller = new TestUploadController
      val request: Request[OverviewUpload] = FakeRequest[OverviewUpload]("POST", "/upload", FakeHeaders(), upload, "controllers.UploadController.create")
      val result = controller.authorizedCreate(guid)(request, null)
      
      status(result)  must be equalTo(PARTIAL_CONTENT)
      
    }
  }
}
