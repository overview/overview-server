package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.mvc.{ AnyContent, Request }
import play.api.Play.{ start, stop }
import play.api.test.{ FakeApplication, FakeRequest }
import play.api.test.Helpers._

import org.overviewproject.tree.Ownership
import controllers.auth.AuthorizedRequest
import models.OverviewUser
import org.overviewproject.tree.orm.DocumentSetUser

class DocumentSetUserControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  trait BaseScope extends Scope {
    val mockStorage = mock[DocumentSetUserController.Storage]
    val controller = new DocumentSetUserController {
      override val storage = mockStorage
    }
    val user = mock[OverviewUser]
    def fakeRequest : Request[AnyContent] = FakeRequest()
    def request = new AuthorizedRequest(fakeRequest, user)
  }

  trait IndexJsonScope extends BaseScope {
    val documentSetId = 1L
    lazy val result = controller.indexJson(documentSetId)(request)
  }

  trait CreateScope extends BaseScope {
    val documentSetId = 1L
    val email = "user@example.org"
    val formBody = Seq("email" -> email, "role" -> "Viewer")
    override def fakeRequest : Request[AnyContent] = FakeRequest()
      .withFormUrlEncodedBody(formBody : _*)
    lazy val result = controller.create(documentSetId)(request)
  }

  trait DeleteScope extends BaseScope {
    val documentSetId = 1L
    val email = "user@example.org"
    lazy val result = controller.delete(documentSetId, email)(request)
  }

  "DocumentSetUserController" should {
    "call loadDocumentSetUsers() with the document set ID and Ownership.Viewer" in new IndexJsonScope {
      mockStorage.loadDocumentSetUsers(documentSetId, Some(Ownership.Viewer)) returns Seq()
      result
      there was one(mockStorage).loadDocumentSetUsers(1L, Some(Ownership.Viewer))
    }

    "show an empty JSON list of users when empty" in new IndexJsonScope {
      mockStorage.loadDocumentSetUsers(documentSetId, Some(Ownership.Viewer)) returns Seq()
      status(result) must beEqualTo(OK)
      contentType(result) must beSome("application/json")
      contentAsString(result) must beEqualTo("""{"viewers":[]}""")
    }

    "show viewers" in new IndexJsonScope {
      val users = Seq(
        DocumentSetUser(documentSetId, "user@example.org", Ownership.Viewer),
        DocumentSetUser(documentSetId, "user2@example.org", Ownership.Viewer)
      )
      mockStorage.loadDocumentSetUsers(documentSetId, Some(Ownership.Viewer)) returns users
      contentAsString(result) must beMatching(""".*"email":"user@example.org".*""".r)
      contentAsString(result) must beMatching(""".*"email":"user2@example.org".*""".r)
    }

    "return OK from create()" in new CreateScope {
      status(result) must beEqualTo(OK)
    }

    "add a user in create()" in new CreateScope {
      result
      there was one(mockStorage).insertOrUpdateDocumentSetUser(any[DocumentSetUser])
    }

    "throw BadRequest when trying to create() the existing user" in new CreateScope {
      user.email returns email
      result
      there were noMoreCallsTo(mockStorage)
    }

    "throw BadRequest from create()" in new CreateScope {
      override val formBody = Seq("email" -> "x", "role" -> "[invalid]")
      status(result) must beEqualTo(BAD_REQUEST)
    }

    "remove a user in delete()" in new DeleteScope {
      result
      there was one(mockStorage).deleteDocumentSetUser(documentSetId, email)
    }

    "throw BadRequest when trying to delete() the existing user" in new DeleteScope {
      user.email returns email
      result
      there were noMoreCallsTo(mockStorage)
    }
  }

  step(stop)
}
