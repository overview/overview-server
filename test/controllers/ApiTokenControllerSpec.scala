package controllers

import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.mvc.{AnyContent,Request}

import controllers.auth.AuthorizedRequest
import org.overviewproject.models.ApiToken

class ApiTokenControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val mockStorage = mock[ApiTokenController.Storage]

    val controller = new ApiTokenController {
      override val storage = mockStorage
    }
  }

  "index" should {
    trait IndexScope extends BaseScope {
      val documentSetId = 1
      def request = fakeAuthorizedRequest
      lazy val result = controller.index(documentSetId)(request)
    }

    "return 200 ok with some HTML" in new IndexScope {
      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("text/html")
    }
  }

  "indexJson" should {
    trait IndexJsonScope extends BaseScope {
      val documentSetId = 1
      lazy val request = fakeAuthorizedRequest.withHeaders("Accept" -> "application/json")
      lazy val result = controller.index(documentSetId)(request)
    }

    "call getTokens()" in new IndexJsonScope {
      mockStorage.getTokens(any, any).returns(Seq())
      result
      there was one(mockStorage).getTokens(request.user.email, documentSetId)
    }

    "respond with JSON" in new IndexJsonScope {
      mockStorage.getTokens(any, any).returns(Seq())
      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("application/json")
    }

    "show the tokens" in new IndexJsonScope {
      val token = ApiToken(
        token="12345",
        createdAt=new java.sql.Timestamp(1405522589794L),
        createdBy="user@example.org",
        documentSetId=documentSetId,
        description="description"
      )
      mockStorage.getTokens(any, any).returns(Seq(token))

      val j = h.contentAsString(result)
      j must /#(0) /("token" -> "12345")
      j must /#(0) /("createdAt" -> "2014-07-16T14:56:29.794Z")
      j must /#(0) /("description" -> "description")
    }
  }

  "createJson" should {
    trait CreateJsonScope extends BaseScope {
      val documentSetId = 1
      lazy val request = fakeAuthorizedRequest.withFormUrlEncodedBody("description" -> "foo")
      lazy val result = controller.create(documentSetId)(request)

      val token = ApiToken(
        token="12345",
        createdAt=new java.sql.Timestamp(1405522589794L),
        createdBy="user@example.org",
        documentSetId=documentSetId,
        description="description"
      )
      mockStorage.createToken(any, any, any).returns(token)
    }

    "create the token" in new CreateJsonScope {
      result
      there was one(mockStorage).createToken(request.user.email, documentSetId, "foo")
    }

    "create the token with a JSON-encoded request" in new BaseScope {
      val documentSetId = 1

      val token = ApiToken(
        token="12345",
        createdAt=new java.sql.Timestamp(1405522589794L),
        createdBy="user@example.org",
        documentSetId=documentSetId,
        description="description"
      )
      mockStorage.createToken(any, any, any).returns(token)

      val request = fakeAuthorizedRequest.withJsonBody(Json.obj("description" -> "foo"))
      val result = controller.create(documentSetId)(request)

      h.status(result) must beEqualTo(h.OK)
      there was one(mockStorage).createToken(request.user.email, documentSetId, "foo")
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
      override lazy val result = controller.create(documentSetId)(fakeAuthorizedRequest)
      h.status(result) must beEqualTo(h.OK)
      there was one(mockStorage).createToken(request.user.email, documentSetId, "")
    }
  }

  "destroy" should {
    trait DestroyScope extends BaseScope {
      val documentSetId = 1
      val token = "12345";

      val request = fakeAuthorizedRequest
      lazy val result = controller.destroy(documentSetId, token)(request)
    }

    "destroy the token" in new DestroyScope {
      result
      there was one(mockStorage).destroyToken(token)
    }

    "return 204 No Content" in new DestroyScope {
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }
  }
}
