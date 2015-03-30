package controllers.api

import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.{ApiTokenBackend,DocumentSetBackend,DocumentSetUserBackend}
import org.overviewproject.models.{DocumentSet,DocumentSetUser}

class DocumentSetControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[DocumentSetBackend]
    val mockApiTokenBackend = mock[ApiTokenBackend]
    val mockDocumentSetUserBackend = mock[DocumentSetUserBackend]
    val controller = new DocumentSetController {
      override val backend = mockBackend
      override val apiTokenBackend = mockApiTokenBackend
      override val documentSetUserBackend = mockDocumentSetUserBackend
    }
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      lazy val req = fakeJsonRequest(Json.obj("title" -> "foo-title"))
      override lazy val result = controller.create(req)

      val documentSet = factory.documentSet(title="foo-title")
      val returnedApiToken = factory.apiToken(documentSetId=Some(documentSet.id))
      mockBackend.create(any) returns Future.successful(documentSet)
      mockDocumentSetUserBackend.createOwner(any, any) returns Future.successful(smartMock[DocumentSetUser])
      mockApiTokenBackend.create(any, any) returns Future.successful(returnedApiToken)
    }

    "create a DocumentSet" in new CreateScope {
      status(result) must beEqualTo(CREATED)
      there was one(mockBackend).create(beLike[DocumentSet.CreateAttributes] { case attributes =>
        val expect = DocumentSet.CreateAttributes(title="foo-title").copy(createdAt=attributes.createdAt)
        attributes must beEqualTo(expect)
      })
    }

    "create a DocumentSetUser" in new CreateScope {
      status(result) must beEqualTo(CREATED)
      there was one(mockDocumentSetUserBackend).createOwner(documentSet.id, req.apiToken.createdBy)
    }

    "return BadRequest on invalid input" in new CreateScope {
      override lazy val req = fakeJsonRequest(Json.obj("title" -> Json.obj("x" -> "y")))
      status(result) must beEqualTo(BAD_REQUEST)
      contentAsString(result) must beEqualTo("""{"message":"You must pass a JSON object with a 'title' attribute."}""")
      there was no(mockBackend).create(any)
    }

    "return a DocumentSet" in new CreateScope {
      val json = contentAsString(result)
      json must /("documentSet") /("id" -> documentSet.id)
      json must /("documentSet") /("title" -> documentSet.title)
    }

    "return an ApiToken" in new CreateScope {
      val json = contentAsString(result)
      json must /("apiToken") /("token" -> returnedApiToken.token)
      json must /("apiToken") /("createdBy" -> returnedApiToken.createdBy)
      json must /("apiToken") /("description" -> returnedApiToken.description)
    }
  }
}
