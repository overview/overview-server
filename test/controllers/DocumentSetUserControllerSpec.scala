package controllers

import org.specs2.specification.Scope
import scala.concurrent.Future

import controllers.backend.{DocumentSetBackend,DocumentSetUserBackend}
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class DocumentSetUserControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockBackend = smartMock[DocumentSetUserBackend]
    val mockDocumentSetBackend = smartMock[DocumentSetBackend]
    val controller = new DocumentSetUserController with TestController {
      override val backend = mockBackend
      override val documentSetBackend = mockDocumentSetBackend
    }
    val documentSetId = 123L
  }

  "#index" should {
    trait IndexScope extends BaseScope {
      mockBackend.index(documentSetId) returns Future.successful(Seq())
      mockDocumentSetBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet()))
      lazy val result = controller.index(documentSetId)(fakeAuthorizedRequest)
    }

    "set data-emails" in new IndexScope {
      mockBackend.index(documentSetId) returns Future.successful(Seq(factory.documentSetUser(1L, "user-x@example.org")))
      h.contentAsString(result) must contain("""data-emails="[&quot;user-x@example.org&quot;]"""")
    }

    "set data-public=true" in new IndexScope {
      mockDocumentSetBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet(isPublic=true)))
      h.contentAsString(result) must contain("""data-public="true"""")
    }

    "set data-public=false" in new IndexScope {
      mockDocumentSetBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet(isPublic=false)))
      h.contentAsString(result) must contain("""data-public="false"""")
    }
  }

  "#update" should {
    trait UpdateScope extends BaseScope {
      val userEmail = "user@example.org"
      mockBackend.update(documentSetId, userEmail) returns Future.successful(None)
      lazy val result = controller.update(documentSetId, userEmail)(fakeAuthorizedRequest)
    }

    "return NotFound" in new UpdateScope {
      mockBackend.update(documentSetId, userEmail) returns Future.successful(None)
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }

    "return NoContent" in new UpdateScope {
      mockBackend.update(documentSetId, userEmail) returns Future.successful(Some(factory.documentSetUser()))
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }

    "return BadRequest" in new UpdateScope {
      override val userEmail = "foo@"
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      val userEmail = "user@example.org"
      mockBackend.destroy(documentSetId, userEmail) returns Future.successful(())
      lazy val result = controller.delete(documentSetId, userEmail)(fakeAuthorizedRequest)
    }

    "return NoContent" in new DestroyScope {
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }
  }
}
