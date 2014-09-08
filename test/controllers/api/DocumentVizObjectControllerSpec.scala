package controllers.api

import play.api.libs.json.{JsNull,JsObject,JsValue,Json}
import scala.concurrent.Future

import controllers.backend.DocumentVizObjectBackend
import org.overviewproject.models.DocumentVizObject

class DocumentVizObjectControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[DocumentVizObjectBackend]
    val controller = new DocumentVizObjectController {
      override val backend = mockBackend
    }
  }

  "DocumentVizObjectController" should {
    "#create" should {
      trait CreateManyScope extends BaseScope {
        val vizId = 1L
        val body: JsValue = Json.arr()
        override lazy val result = controller.create(vizId)(fakeJsonRequest(body))
      }

      "pass the correct body to backend.createMany" in new CreateManyScope {
        override val body = Json.arr(
          Json.arr(1L, 2L, Json.obj("foo" -> "bar")),
          Json.arr(3L, 4L),
          Json.arr(5L, 6L, JsNull)
        )
        mockBackend.createMany(any[Long], any[Seq[DocumentVizObject]]) returns Future.successful(Seq())
        status(result) must beEqualTo(CREATED)
        there was one(mockBackend).createMany(vizId, Seq(
          DocumentVizObject(1L, 2L, Some(Json.obj("foo" -> "bar"))),
          DocumentVizObject(3L, 4L, None),
          DocumentVizObject(5L, 6L, None)
        ))
      }

      "return a JSON Array of Arrays" in new CreateManyScope {
        mockBackend.createMany(any[Long], any[Seq[DocumentVizObject]]) returns Future.successful(Seq(
          DocumentVizObject(1L, 2L, Some(Json.obj("foo" -> "bar"))),
          DocumentVizObject(3L, 4L, None),
          DocumentVizObject(5L, 6L, Some(Json.obj("bar" -> "baz")))
        ))
        status(result) must beEqualTo(CREATED)
        contentType(result) must beSome("application/json")
        contentAsString(result) must beEqualTo("""[[1,2,{"foo":"bar"}],[3,4],[5,6,{"bar":"baz"}]]""")
      }

      "warn on invalid JSON input" in new CreateManyScope {
        override val body: JsValue = Json.obj(
          "documentId" -> 1L,
          "vizObjectId" -> 2L,
          "json" -> Json.obj("foo" -> "bar")
        )
        status(result) must beEqualTo(BAD_REQUEST)
        contentType(result) must beSome("application/json")
        contentAsString(result) must /("message" -> """You must POST a JSON Array of Array elements. Each element should look like [documentId,objectId] or [documentId,objectId,null...] or [documentId,objectId,{"arbitrary":"json object"}].""")
      }
    }
  }
}
