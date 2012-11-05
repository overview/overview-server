package controllers

import java.util.UUID
import models.orm.User
import models.upload.OverviewUpload
import org.specs2.mutable.Specification
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ Action, Result }
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.specs2.mock.Mockito

class UploadControllerSpec extends Specification with Mockito {

  class TestUploadController extends UploadController {

    def fileUploadIteratee(userId: Long, guid: UUID, uploadInfo: UploadInfo): Iteratee[Array[Byte], Either[Result, OverviewUpload]] = Done(Right(mock[OverviewUpload]), Input.EOF)
  }

  "UploadController" should {
    "return ok if headers are good" in {
      val user = User()
      val guid = UUID.randomUUID

      val controller = new TestUploadController

      val headers = FakeHeaders(Map(
          ("CONTENT-DISPOSITION", Seq("attachment;filename=foo.bar")), 
          ("CONTENT-LENGTH", Seq("1000"))))
          
      val request = FakeRequest[Option[OverviewUpload]]("POST", "/uploads", headers, None, "controllers.UploadController.create")

      val resultPromise = controller.fileUploadBodyParser(user, guid)(request).run
      val result = resultPromise.await.get

      result must beRight
    }

  }
}
