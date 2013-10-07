package controllers

import play.api.Play.{ start, stop }
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import org.specs2.mock.Mockito
import org.overviewproject.tree.orm.GroupedFileUpload
import java.util.UUID
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Result
import models.OverviewUser
import models.orm.User
import controllers.auth.AuthorizedRequest
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.mvc.RequestHeader
import org.overviewproject.tree.orm.FileGroup
import org.specs2.specification.Scope

class MassUploadControllerSpec extends Specification with Mockito {

  step(start(FakeApplication()))

  class TestMassUploadController extends MassUploadController {
    // We can leave this undefined, since it will not be called.
    def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID, lastModifiedDate: String): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] =
      ???
      
    override val storage = smartMock[Storage]
  }

  "MassUploadController.show" should {

    trait UploadContext extends Scope {
      val guid = UUID.randomUUID
      val user = OverviewUser(User(1l))
      val request = new AuthorizedRequest(FakeRequest(), user)
      val controller = new TestMassUploadController
      
      lazy val result: Result = controller.show(guid)(request)
    }
    
    "return NOT_FOUND if upload does not exist" in new UploadContext {
      val fileGroup = smartMock[FileGroup]

      controller.storage.findGroupedFileUpload(guid) returns None
      controller.storage.findFileGroupInProgress(user.email) returns Some(fileGroup)
      
      status(result) must be equalTo(NOT_FOUND)
    }

    "return NOT_FOUND if no InProgress FileGroup exists" in new UploadContext {
      val upload = smartMock[GroupedFileUpload]

      controller.storage.findGroupedFileUpload(guid) returns Some(upload)
      controller.storage.findFileGroupInProgress(user.email) returns None
      
      status(result) must be equalTo(NOT_FOUND)

    }

    "return Ok with content length if upload is complete" in new UploadContext {
      val uploadSize = 1000l
      val filename = "foo.pdf"
      val contentDisposition = s"attachment ; filename=$filename"
      val fileGroup = smartMock[FileGroup]
      val upload = smartMock[GroupedFileUpload]

      controller.storage.findFileGroupInProgress(user.email) returns Some(fileGroup)
      controller.storage.findGroupedFileUpload(guid) returns Some(upload)
      
      upload.size returns uploadSize
      upload.uploadedSize returns uploadSize
      upload.name returns filename
      
      status(result) must be equalTo(OK)
      header(CONTENT_LENGTH, result) must beSome(s"$uploadSize")
      header(CONTENT_DISPOSITION, result) must beSome(contentDisposition)
    }

    "return PartialContent with content range if upload is not complete" in {
      pending
    }
    
    "return NOT_FOUND if user does not own upload" in {
      pending
    }
  }
  step(stop)
}