package controllers

import org.specs2.specification.Scope

import models.orm.User

class TourControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = mock[TourController.Storage]
    val controller = new TourController {
      override val storage = mockStorage
    }
  }

  "TourController" should {
    "delete" should {
      trait DeleteScope extends BaseScope {
        lazy val request = fakeAuthorizedRequest
        lazy val result = controller.delete()(request)
      }

      "return 204 NoContent" in new DeleteScope {
        h.status(result) must beEqualTo(h.NO_CONTENT)
      }

      "set the user to have treeTooltipsEnabled=false" in new DeleteScope {
        result // lazy eval
        there was one(mockStorage).disableTreeTooltipsForEmail(request.user.email)
      }
    }
  }
}
