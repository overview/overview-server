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
import play.api.test.Helpers.{ NOT_FOUND, OK, PARTIAL_CONTENT }
import play.api.test.Helpers.status
import org.specs2.specification.Scope
import models.orm.User
import play.api.mvc.AnyContent

@RunWith(classOf[JUnitRunner])
class UploadControllerSpec extends Specification with Mockito {

  class TestUploadController extends UploadController {

    def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] =
      Done(Right(mock[OverviewUpload]), Input.EOF)

    def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = None
  }

  trait UploadContext extends Scope {
    val guid = UUID.randomUUID

    def upload: OverviewUpload
    val controller = new TestUploadController
    val request: Request[OverviewUpload] = FakeRequest[OverviewUpload]("POST", "/upload", FakeHeaders(), upload, "controllers.UploadController.create")
    val result = controller.authorizedCreate(guid)(request, null)
  }

  trait HeadRequest extends Scope {
    val user = User(1l)
    val guid = UUID.randomUUID

    val controller = new TestUploadController
    val request: Request[AnyContent] = FakeRequest()
    val result = controller.authorizedShow(user, guid)(request, null)
  }
  
  trait CompleteUpload extends UploadContext {
    override def upload: OverviewUpload = {
      val u = mock[OverviewUpload]
      u.bytesUploaded returns 1000
      u.size returns 1000
    }
  }

  trait IncompleteUpload extends UploadContext {
    override def upload: OverviewUpload = {
      val u = mock[OverviewUpload]
      u.bytesUploaded returns 100
      u.size returns 1000
    }
  }

  "UploadController.create" should {
    "return OK if upload is complete" in new CompleteUpload {
      status(result) must be equalTo (OK)
    }

    "return PARTIAL_CONTENT if upload is not complete" in new IncompleteUpload {
      status(result) must be equalTo (PARTIAL_CONTENT)
    }
  }

  "UploadController.show" should {
    "return NOT_FOUND if upload does not exist" in new HeadRequest {
      status(result) must be equalTo(NOT_FOUND)
    }
  }
}
