package controllers

import org.mockito.Matchers
import org.specs2.specification.Scope
import scala.concurrent.Future

import controllers.auth.AuthConfig
import controllers.backend.{DocumentSetBackend,DocumentSetUserBackend}
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class DocumentSetUserControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockBackend = smartMock[DocumentSetUserBackend]
    val mockDocumentSetBackend = smartMock[DocumentSetBackend]
    val authConfig = smartMock[AuthConfig]
    authConfig.isAdminOnlyExport returns false
    val controller = new DocumentSetUserController(
      mockBackend,
      mockDocumentSetBackend,
      fakeControllerComponents,
      authConfig,
      mockView[views.html.DocumentSetUser.index]
    )
    val documentSetId = 123L
  }

  "#index" should {
    trait IndexScope extends BaseScope {
      mockBackend.index(documentSetId) returns Future.successful(Vector())
      mockDocumentSetBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet()))
      lazy val result = controller.index(documentSetId)(fakeAuthorizedRequest)
    }

    "set data-emails" in new IndexScope {
      mockBackend.index(documentSetId) returns Future.successful(Vector(factory.documentSetUser(1L, "user-x@example.org")))
      h.contentAsString(result)
      there was one(controller.indexHtml).apply(any, any, Matchers.eq(Vector("user-x@example.org")), any)(any, any, any)
    }

    "set data-public=true" in new IndexScope {
      mockDocumentSetBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet(isPublic=true)))
      h.contentAsString(result)
      there was one(controller.indexHtml).apply(any, any, any, Matchers.eq(true))(any, any, any)
    }

    "set data-public=false" in new IndexScope {
      mockDocumentSetBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet(isPublic=false)))
      h.contentAsString(result)
      there was one(controller.indexHtml).apply(any, any, any, Matchers.eq(false))(any, any, any)
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
      mockBackend.destroy(documentSetId, userEmail) returns Future.unit
      lazy val result = controller.delete(documentSetId, userEmail)(fakeAuthorizedRequest)
    }

    "return NoContent" in new DestroyScope {
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }
  }
}
