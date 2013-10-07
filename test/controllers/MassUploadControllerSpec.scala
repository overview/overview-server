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

class MassUploadControllerSpec extends Specification with Mockito {

  step(start(FakeApplication()))

  class TestMassUploadController extends MassUploadController {
    // We can leave this undefined, since it will not be called.
    def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID, lastModifiedDate: String): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] =
      ???
      
    override val storage = mock[Storage]
  }

  "MassUploadController.show" should {

    "return NOT_FOUND if upload does not exist" in {
      val guid = UUID.randomUUID
      val user = OverviewUser(User(1l))
      val request = new AuthorizedRequest(FakeRequest(), user)
      val controller = new TestMassUploadController
      controller.storage.findGroupedFileUpload(guid) returns None
      
      val result = controller.show(guid)(request)
      
      status(result) must be equalTo(NOT_FOUND)
    }

    "return NOT_FOUND if no InProgress FileGroup exists" in {
      pending
    }

    "return Ok with content length if upload is complete" in {
      pending
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