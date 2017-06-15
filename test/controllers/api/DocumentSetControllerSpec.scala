package controllers.api

import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.{ApiTokenBackend,DocumentSetBackend}
import com.overviewdocs.metadata.{MetadataSchema,MetadataField,MetadataFieldType}
import com.overviewdocs.models.DocumentSet

class DocumentSetControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[DocumentSetBackend]
    val mockApiTokenBackend = mock[ApiTokenBackend]
    val controller = new DocumentSetController(mockApiTokenBackend, mockBackend)
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      lazy val req = fakeJsonRequest(Json.obj("title" -> "foo-title"))
      override lazy val result = controller.create(req)

      val documentSet = factory.documentSet(title="foo-title")
      val returnedApiToken = factory.apiToken(documentSetId=Some(documentSet.id))
      mockBackend.create(any, any) returns Future.successful(documentSet)
      mockApiTokenBackend.create(any, any) returns Future.successful(returnedApiToken)
    }

    "create a DocumentSet" in new CreateScope {
      status(result) must beEqualTo(CREATED)
      there was one(mockBackend).create(
        beLike[DocumentSet.CreateAttributes] { case attributes =>
          val expect = DocumentSet.CreateAttributes(title="foo-title").copy(createdAt=attributes.createdAt)
          attributes must beEqualTo(expect)
        },
        beLike[String] { case s => s must beEqualTo(req.apiToken.createdBy) }
      )
    }

    "add metadataSchema" in new CreateScope {
      override lazy val req = fakeJsonRequest(Json.obj(
        "title" -> "foo-title",
        "metadataSchema" -> Json.obj(
          "version" -> 1,
          "fields" -> Json.arr(
            Json.obj("name" -> "foo", "type" -> "String"),
            Json.obj("name" -> "bar", "type" -> "String")
          )
        )
      ))
      status(result) must beEqualTo(CREATED)

      there was one(mockBackend).create(
        beLike[DocumentSet.CreateAttributes] { case attributes =>
          val expect = DocumentSet.CreateAttributes(
            title="foo-title",
            metadataSchema=MetadataSchema(1, Seq(
              MetadataField("foo", MetadataFieldType.String),
              MetadataField("bar", MetadataFieldType.String)
            ))
          ).copy(createdAt=attributes.createdAt)
          attributes must beEqualTo(expect)
        },
        beLike[String] { case s => s must beEqualTo(req.apiToken.createdBy) }
      )
    }

    "return BadRequest on invalid input" in new CreateScope {
      override lazy val req = fakeJsonRequest(Json.obj("title" -> Json.obj("x" -> "y")))
      status(result) must beEqualTo(BAD_REQUEST)
      contentAsString(result) must /("code" -> "illegal-arguments")
      there was no(mockBackend).create(any, any)
    }

    "return a DocumentSet" in new CreateScope {
      val json = contentAsString(result)
      json must /("documentSet") /("id" -> documentSet.id.toInt)
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
