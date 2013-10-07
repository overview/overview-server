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
import org.specs2.mutable.Before

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

    trait NoFileGroup {
      this: UploadContext =>

      controller.storage.findFileGroupInProgress(user.email) returns None
    }

    trait InProgressFileGroup {
      this: UploadContext =>

      val fileGroup = smartMock[FileGroup]
      controller.storage.findFileGroupInProgress(user.email) returns Some(fileGroup)

    }

    trait NoUpload {
      this: UploadContext =>

      controller.storage.findGroupedFileUpload(guid) returns None
    }
    
    trait CompleteUpload {
      this: UploadContext =>

      val uploadSize = 1000l
      val filename = "foo.pdf"
      val contentDisposition = s"attachment ; filename=$filename"
      val upload = smartMock[GroupedFileUpload]

      controller.storage.findGroupedFileUpload(guid) returns Some(upload)

      upload.size returns uploadSize
      upload.uploadedSize returns uploadSize
      upload.name returns filename

    }

    "return NOT_FOUND if upload does not exist" in new UploadContext with NoUpload with InProgressFileGroup {
      status(result) must be equalTo (NOT_FOUND)
    }

    "return NOT_FOUND if no InProgress FileGroup exists" in new UploadContext with CompleteUpload with NoFileGroup {
      status(result) must be equalTo (NOT_FOUND)
    }

    "return Ok with content length if upload is complete" in new UploadContext with CompleteUpload with InProgressFileGroup {
      status(result) must be equalTo (OK)
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