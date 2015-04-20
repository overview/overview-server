package controllers.api

import play.api.libs.json.{JsNull,JsObject,JsValue,Json}
import scala.concurrent.Future

import models.{Selection,SelectionRequest}
import controllers.backend.{StoreBackend,DocumentStoreObjectBackend,SelectionBackend}
import org.overviewproject.models.{DocumentStoreObject,Store}

class DocumentStoreObjectControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockStoreBackend = smartMock[StoreBackend]
    val mockObjectBackend = smartMock[DocumentStoreObjectBackend]
    val mockSelectionBackend = smartMock[SelectionBackend]
    val controller = new DocumentStoreObjectController {
      override val storeBackend = mockStoreBackend
      override val documentStoreObjectBackend = mockObjectBackend
      override val selectionBackend = mockSelectionBackend
    }
  }

  "DocumentStoreObjectController" should {
    "#countByObject" should {
      trait CountByObjectScope extends BaseScope {
        val mockSelection = mock[Selection]
        mockStoreBackend.showOrCreate(any[String]) returns Future.successful(Store(123L, "foobar", Json.obj()))
        mockSelectionBackend.findOrCreate(any, any) returns Future.successful(mockSelection)

        override lazy val request = fakeRequest("GET", "")
        override def action = controller.countByObject()
      }

      "return all counts as a JsObject" in new CountByObjectScope {
        mockObjectBackend.countByObject(123L, mockSelection) returns Future.successful(Map(1L -> 2, 3L -> 4))
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")

        val json = contentAsString(result)
        json must /("1" -> 2)
        json must /("3" -> 4)
      }

      "grab selectionRequest from the HTTP request" in new CountByObjectScope {
        override lazy val request = fakeRequest("GET", "/?q=foo")
        mockObjectBackend.countByObject(123L, mockSelection) returns Future.successful(Map(1L -> 2, 3L -> 4))
        status(result)
        there was one(mockSelectionBackend).findOrCreate(request.apiToken.createdBy, SelectionRequest(apiToken.documentSetId.get, q="foo"))
      }
    }

    "#createMany" should {
      trait CreateManyScope extends BaseScope {
        val body: JsValue = Json.arr()
        mockStoreBackend.showOrCreate(any[String]) returns Future.successful(Store(123L, "foobar", Json.obj()))
        lazy val _request = fakeJsonRequest(body)
        override lazy val result = controller.createMany(_request)
      }

      "create the Store if it does not exist" in new CreateManyScope {
        mockObjectBackend.createMany(any[Long], any[Seq[DocumentStoreObject]]) returns Future.successful(Seq())
        status(result)
        there was one(mockStoreBackend).showOrCreate(_request.apiToken.token)
      }

      "pass the correct body to backend.createMany" in new CreateManyScope {
        override val body = Json.arr(
          Json.arr(1L, 2L, Json.obj("foo" -> "bar")),
          Json.arr(3L, 4L),
          Json.arr(5L, 6L, JsNull)
        )
        mockObjectBackend.createMany(any[Long], any[Seq[DocumentStoreObject]]) returns Future.successful(Seq())
        status(result) must beEqualTo(CREATED)
        there was one(mockObjectBackend).createMany(123L, Seq(
          DocumentStoreObject(1L, 2L, Some(Json.obj("foo" -> "bar"))),
          DocumentStoreObject(3L, 4L, None),
          DocumentStoreObject(5L, 6L, None)
        ))
      }

      "return a JSON Array of Arrays" in new CreateManyScope {
        mockObjectBackend.createMany(any[Long], any[Seq[DocumentStoreObject]]) returns Future.successful(Seq(
          DocumentStoreObject(1L, 2L, Some(Json.obj("foo" -> "bar"))),
          DocumentStoreObject(3L, 4L, None),
          DocumentStoreObject(5L, 6L, Some(Json.obj("bar" -> "baz")))
        ))
        status(result) must beEqualTo(CREATED)
        contentType(result) must beSome("application/json")
        contentAsString(result) must beEqualTo("""[[1,2,{"foo":"bar"}],[3,4],[5,6,{"bar":"baz"}]]""")
      }

      "error on invalid JSON input" in new CreateManyScope {
        override val body: JsValue = Json.obj(
          "documentId" -> 1L,
          "storeObjectId" -> 2L,
          "json" -> Json.obj("foo" -> "bar")
        )
        status(result) must beEqualTo(BAD_REQUEST)
        contentType(result) must beSome("application/json")
        contentAsString(result) must /("message" -> """You must POST a JSON Array of Array elements. Each element should look like [documentId,objectId] or [documentId,objectId,null...] or [documentId,objectId,{"arbitrary":"json object"}].""")
      }
    }

    "#destroyMany" should {
      trait DestroyManyScope extends BaseScope {
        val body: JsValue = Json.arr()
        override lazy val result = controller.destroyMany(fakeJsonRequest(body))
        mockStoreBackend.showOrCreate(any) returns Future.successful(Store(123L, "foobar", Json.obj()))
        mockObjectBackend.destroyMany(any[Long], any[Seq[(Long,Long)]]) returns Future.successful(())
      }

      "pass the correct body to backend.destroyMany" in new DestroyManyScope {
        override val body = Json.arr(
          Json.arr(1L, 2L),
          Json.arr(3L, 4L)
        )
        status(result) must beEqualTo(NO_CONTENT)
        there was one(mockObjectBackend).destroyMany(123L, Seq(1L -> 2L, 3L -> 4L))
      }

      "error on invalid JSON input" in new DestroyManyScope {
        override val body: JsValue = Json.arr(
          Json.arr(1L, "foo"),
          Json.arr(3L, 4L)
        )
        status(result) must beEqualTo(BAD_REQUEST)
        contentType(result) must beSome("application/json")
        contentAsString(result) must /("message" -> """You must POST a JSON Array of Array elements. Each element should look like [documentId,objectId].""")
      }
    }
  }
}
