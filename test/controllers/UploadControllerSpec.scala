package controllers

import java.util.UUID
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import models.upload.OverviewUpload
import models.upload.OverviewUploadedFile
import play.api.http.HeaderNames._
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
import play.api.mvc.SimpleResult

@RunWith(classOf[JUnitRunner])
class UploadControllerSpec extends Specification with Mockito {

  class TestUploadController(upload: Option[OverviewUpload] = None) extends UploadController {
    var uploadDeleted: Boolean = false
    var jobStarted: Boolean = false
    
    def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] =
      Done(Right(mock[OverviewUpload]), Input.EOF)

    def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = upload
    def deleteUpload(upload: OverviewUpload) { uploadDeleted = true }
    def startDocumentSetCreationJob(upload: OverviewUpload) { jobStarted = true }
  }

  trait UploadContext[A] extends Scope {
    val guid = UUID.randomUUID

    def upload: OverviewUpload
    val controller: TestUploadController
    val request: Request[A]
    val result: Result
  }

  trait CreateRequest extends UploadContext[OverviewUpload] {
    val controller = new TestUploadController
    val request = FakeRequest[OverviewUpload]("POST", "/upload", FakeHeaders(), upload, "controllers.UploadController.create")
    val result = controller.authorizedCreate(guid)(request, null)
  }

  trait HeadRequest extends UploadContext[AnyContent] {
    val user = User(1l)
    val controller = new TestUploadController(Option(upload))
    val request: Request[AnyContent] = FakeRequest()
    val result = controller.authorizedShow(user, guid)(request, null)
  }

  trait NoStartedUpload {
    def upload: OverviewUpload = null
  }

  trait StartedUpload {
    def contentDisposition = "attachment; filename=file.name"
    def bytesUploaded: Long

    def upload: OverviewUpload = {
      val u = mock[OverviewUpload]
      val f = mock[OverviewUploadedFile]
      u.uploadedFile returns f
      f.size returns bytesUploaded
      f.contentDisposition returns contentDisposition
      u.size returns 1000
    }
  }

  trait CompleteUpload extends StartedUpload {
    override def bytesUploaded: Long = 1000
  }

  trait IncompleteUpload extends StartedUpload {
    override def bytesUploaded: Long = 100
  }
  
  trait EmptyUpload extends StartedUpload {
    override def bytesUploaded: Long = 0
  }

  "UploadController.create" should {
    "return OK if upload is complete" in new CreateRequest with CompleteUpload {
      status(result) must be equalTo (OK)
    }

    "return PARTIAL_CONTENT if upload is not complete" in new CreateRequest with IncompleteUpload {
      status(result) must be equalTo (PARTIAL_CONTENT)
    }
    
    "start a DocumentSetCreationJob and delete the upload" in new CreateRequest with CompleteUpload {
      status(result) must be equalTo(OK)
      controller.jobStarted must beTrue
      controller.uploadDeleted must beTrue
    }
    
    "not start a DocumentSetCreationJob if upload is not complete" in new CreateRequest with IncompleteUpload {
      status(result) must be equalTo(PARTIAL_CONTENT)
      controller.jobStarted must beFalse
    }
  }

  "UploadController.show" should {
    "return NOT_FOUND if upload does not exist" in new HeadRequest with NoStartedUpload {
      status(result) must be equalTo (NOT_FOUND)
    }

    "return OK with upload info in headers if upload is complete" in new HeadRequest with CompleteUpload {
      val headers = result match {
        case SimpleResult(header, _) => header.headers
      }
      headers.get(CONTENT_RANGE) must beSome.like { case r => r must be equalTo ("0-999/1000") }
      headers.get(CONTENT_DISPOSITION) must beSome.like { case d => d must be equalTo (contentDisposition) }

      status(result) must be equalTo (OK)
    }

    "return PARTIAL_CONTENT if upload is not complete" in new HeadRequest with IncompleteUpload {
      result.header.headers.get(CONTENT_RANGE) must beSome.like { case r => r must be equalTo ("0-99/1000") }

      status(result) must be equalTo (PARTIAL_CONTENT)
    }
    
    "return NOT_FOUND if upload is empty" in new HeadRequest with EmptyUpload {
      result.header.headers.get(CONTENT_RANGE) must beNone
      status(result) must be equalTo (NOT_FOUND)
    }
  }
}
