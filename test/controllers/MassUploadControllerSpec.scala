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

    trait FileGroupProvider {
      def createFileGroup: Option[FileGroup]
    }

    trait UploadProvider {
      def createUpload: Option[GroupedFileUpload]
    }

    trait UploadContext extends Before with FileGroupProvider with UploadProvider {
      val fileGroupId = 1l
      val guid = UUID.randomUUID
      val user = OverviewUser(User(1l))
      val request = new AuthorizedRequest(FakeRequest(), user)
      val controller = new TestMassUploadController

      def before = {
        val fileGroup = createFileGroup
        controller.storage.findCurrentFileGroup(user.email) returns fileGroup
        fileGroup map { fg => fg.id returns fileGroupId }
        
        controller.storage.findGroupedFileUpload(fileGroupId, guid) returns createUpload
      }

      lazy val result: Result = controller.show(guid)(request)
    }

    trait NoFileGroup extends FileGroupProvider {
      override def createFileGroup() = None
    }

    trait InProgressFileGroup extends FileGroupProvider {
      override def createFileGroup = Some(smartMock[FileGroup])
    }

    trait NoUpload extends UploadProvider {
      override def createUpload = None
    }

    trait CompleteUpload extends UploadProvider {

      val size = 1000l
      val filename = "foo.pdf"
      val contentDisposition = s"attachment ; filename=$filename"

      override def createUpload = {
        val upload = smartMock[GroupedFileUpload]

        upload.size returns size
        upload.uploadedSize returns size
        upload.name returns filename

        Some(upload)
      }
    }

    trait IncompleteUpload extends UploadProvider {
      val size = 1000l
      val uploadSize = 500l
      val filename = "foo.pdf"
      val contentDisposition = s"attachment ; filename=$filename"

      override def createUpload = {
        val upload = smartMock[GroupedFileUpload]

        upload.size returns size
        upload.uploadedSize returns uploadSize
        upload.name returns filename

        Some(upload)
      }

    }

    "return NOT_FOUND if upload does not exist" in new UploadContext with NoUpload with InProgressFileGroup {
      status(result) must be equalTo (NOT_FOUND)
    }

    "return NOT_FOUND if no InProgress FileGroup exists" in new UploadContext with CompleteUpload with NoFileGroup {
      status(result) must be equalTo (NOT_FOUND)
    }

    "return Ok with content length if upload is complete" in new UploadContext with CompleteUpload with InProgressFileGroup {
      status(result) must be equalTo (OK)
      header(CONTENT_LENGTH, result) must beSome(s"$size")
      header(CONTENT_DISPOSITION, result) must beSome(contentDisposition)
    }

    "return PartialContent with content range if upload is not complete" in new UploadContext with IncompleteUpload with InProgressFileGroup {
      status(result) must be equalTo (PARTIAL_CONTENT)
      header(CONTENT_RANGE, result) must beSome(s"0-${uploadSize - 1}/$size")
      header(CONTENT_DISPOSITION, result) must beSome(contentDisposition)
    }

  }
  step(stop)
}