package controllers

import org.specs2.specification.Scope
import scala.concurrent.Future

import controllers.backend.DocumentSetFileBackend

class DocumentSetFileControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockBackend = mock[DocumentSetFileBackend]
    val controller = new DocumentSetFileController(mockBackend, fakeControllerComponents)
  }

  "#head" should {
    trait HeadScope extends BaseScope {
      val documentSetId = 123L
      val sha1 = Array(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19).map(_.toByte)
      lazy val result = controller.head(documentSetId, sha1)(fakeAuthorizedRequest)
    }

    "return 204 No Content when a match exists" in new HeadScope {
      mockBackend.existsByIdAndSha1(documentSetId, sha1) returns Future.successful(true)
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }

    "return 404 Not Found when no match exists" in new HeadScope {
      mockBackend.existsByIdAndSha1(documentSetId, sha1) returns Future.successful(false)
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }
  }
}
