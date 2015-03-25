package controllers

import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.ApiTokenBackend
import org.overviewproject.models.ApiToken

class ApiTokenControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val mockBackend = smartMock[ApiTokenBackend]

    val controller = new ApiTokenController {
      override val backend = mockBackend
    }
  }

  "index" should {
    trait IndexScope extends BaseScope {
      val documentSetId = 1
      def request = fakeAuthorizedRequest
      lazy val result = controller.indexForDocumentSet(documentSetId)(request)
    }

    "return 200 ok with some HTML" in new IndexScope {
      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("text/html")
    }

    "work with documentSetId=None" in new IndexScope {
      override lazy val result = controller.index(request)
      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("text/html")
    }
  }

  "indexJson" should {
    trait IndexJsonScope extends BaseScope {
      val documentSetId = 1
      lazy val request = fakeAuthorizedRequest.withHeaders("Accept" -> "application/json")
      lazy val result = controller.indexForDocumentSet(documentSetId)(request)
    }

    "call getTokens()" in new IndexJsonScope {
      mockBackend.index(any, any) returns Future.successful(Seq())
      result
      there was one(mockBackend).index(request.user.email, Some(documentSetId))
    }

    "respond with JSON" in new IndexJsonScope {
      mockBackend.index(any, any) returns Future.successful(Seq())
      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("application/json")
    }

    "show the tokens" in new IndexJsonScope {
      val token = ApiToken(
        token="12345",
        createdAt=new java.sql.Timestamp(1405522589794L),
        createdBy="user@example.org",
        documentSetId=Some(documentSetId),
        description="description"
      )
      mockBackend.index(any, any) returns Future.successful(Seq(token))

      val j = h.contentAsString(result)
      j must /#(0) /("token" -> "12345")
      j must /#(0) /("createdAt" -> "2014-07-16T14:56:29.794Z")
      j must /#(0) /("description" -> "description")
    }

    "work with documentSetId=None" in new IndexJsonScope {
      override lazy val result = controller.index(request)
      mockBackend.index(any, any) returns Future.successful(Seq())
      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("application/json")
      there was one(mockBackend).index(request.user.email, None)
    }
  }

  "createJson" should {
    trait CreateJsonScope extends BaseScope {
      val documentSetId = 1
      lazy val request = fakeAuthorizedRequest.withFormUrlEncodedBody("description" -> "foo")
      lazy val result = controller.createForDocumentSet(documentSetId)(request)

      val token = ApiToken(
        token="12345",
        createdAt=new java.sql.Timestamp(1405522589794L),
        createdBy="user@example.org",
        documentSetId=Some(documentSetId),
        description="description"
      )
      mockBackend.create(any, any) returns Future.successful(token)
    }

    "create the token" in new CreateJsonScope {
      result
      there was one(mockBackend).create(Some(documentSetId), ApiToken.CreateAttributes(request.user.email, "foo"))
    }

    "create the token with a JSON-encoded request" in new BaseScope {
      val documentSetId = 1

      val token = ApiToken(
        token="12345",
        createdAt=new java.sql.Timestamp(1405522589794L),
        createdBy="user@example.org",
        documentSetId=Some(documentSetId),
        description="description"
      )
      mockBackend.create(any, any) returns Future.successful(token)

      val request = fakeAuthorizedRequest.withJsonBody(Json.obj("description" -> "foo"))
      val result = controller.createForDocumentSet(documentSetId)(request)

      h.status(result) must beEqualTo(h.OK)
      there was one(mockBackend).create(Some(documentSetId), ApiToken.CreateAttributes(request.user.email, "foo"))
    }

    "return the object as JSON" in new CreateJsonScope {
      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("application/json")

      val j = h.contentAsString(result)
      j must /("token" -> "12345")
      j must /("createdAt" -> "2014-07-16T14:56:29.794Z")
      j must /("description" -> "description")
    }

    "create an object with an empty description if the request is wrong" in new CreateJsonScope {
      override lazy val result = controller.createForDocumentSet(documentSetId)(fakeAuthorizedRequest)
      h.status(result) must beEqualTo(h.OK)
      there was one(mockBackend).create(Some(documentSetId), ApiToken.CreateAttributes(request.user.email, ""))
    }

    "work when documentSetId=None" in new CreateJsonScope {
      override lazy val result = controller.create(request)

      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("application/json")

      there was one(mockBackend).create(None, ApiToken.CreateAttributes(request.user.email, "foo"))
    }
  }

  "destroy" should {
    trait DestroyScope extends BaseScope {
      val documentSetId = 1
      val token = "12345";

      val request = fakeAuthorizedRequest
      lazy val result = controller.destroyForDocumentSet(documentSetId, token)(request)

      mockBackend.destroy(any) returns Future.successful(())
    }

    "destroy the token" in new DestroyScope {
      result
      there was one(mockBackend).destroy(token)
    }

    "return 204 No Content" in new DestroyScope {
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }

    "work when documentSetId=None" in new DestroyScope {
      override lazy val result = controller.destroy(token)(request)
      h.status(result) must beEqualTo(h.NO_CONTENT)
      there was one(mockBackend).destroy(token)
    }
  }
}
