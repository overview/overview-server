package controllers

import org.specs2.specification.Scope
import play.api.mvc.{ Request, AnyContent }

import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.DocumentSetUser

class DocumentSetUserControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = mock[DocumentSetUserController.Storage]
    val controller = new DocumentSetUserController {
      override val storage = mockStorage
    }
  }

  trait IndexJsonScope extends BaseScope {
    val documentSetId = 1L
    lazy val result = controller.indexJson(documentSetId)(fakeAuthorizedRequest)
  }

  trait CreateScope extends BaseScope {
    val documentSetId = 1L
    val email = "user1@example.org"
    val formBody = Seq("email" -> email, "role" -> "Viewer")
    def request = fakeAuthorizedRequest(fakeUser).withFormUrlEncodedBody(formBody : _*)
    lazy val result = controller.create(documentSetId)(request)
  }

  trait DeleteScope extends BaseScope {
    val documentSetId = 1L
    val email = "user1@example.org"
    lazy val result = controller.delete(documentSetId, email)(fakeAuthorizedRequest)
  }

  "DocumentSetUserController" should {
    "call loadDocumentSetUsers() with the document set ID and Ownership.Viewer" in new IndexJsonScope {
      mockStorage.loadDocumentSetUsers(documentSetId, Some(Ownership.Viewer)) returns Seq()
      result
      there was one(mockStorage).loadDocumentSetUsers(1L, Some(Ownership.Viewer))
    }

    "show an empty JSON list of users when empty" in new IndexJsonScope {
      mockStorage.loadDocumentSetUsers(documentSetId, Some(Ownership.Viewer)) returns Seq()
      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("application/json")
      h.contentAsString(result) must beEqualTo("""{"viewers":[]}""")
    }

    "show viewers" in new IndexJsonScope {
      val users = Seq(
        DocumentSetUser(documentSetId, "user1@example.org", Ownership.Viewer),
        DocumentSetUser(documentSetId, "user2@example.org", Ownership.Viewer)
      )
      mockStorage.loadDocumentSetUsers(documentSetId, Some(Ownership.Viewer)) returns users
      h.contentAsString(result) must beMatching(""".*"email":"user1@example.org".*""".r)
      h.contentAsString(result) must beMatching(""".*"email":"user2@example.org".*""".r)
    }

    "return OK from create()" in new CreateScope {
      h.status(result) must beEqualTo(h.OK)
    }

    "add a user in create()" in new CreateScope {
      result
      there was one(mockStorage).insertOrUpdateDocumentSetUser(any[DocumentSetUser])
    }

    "throw BadRequest when trying to create() the existing user" in new CreateScope {
      override val email = fakeUser.email
      result
      there were noMoreCallsTo(mockStorage)
    }

    "throw BadRequest from create()" in new CreateScope {
      override val formBody = Seq("email" -> "x", "role" -> "[invalid]")
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }

    "remove a user in delete()" in new DeleteScope {
      result
      there was one(mockStorage).deleteDocumentSetUser(documentSetId, email)
    }

    "throw BadRequest when trying to delete() the existing user" in new DeleteScope {
      override val email = fakeUser.email
      result
      there were noMoreCallsTo(mockStorage)
    }
  }
}
