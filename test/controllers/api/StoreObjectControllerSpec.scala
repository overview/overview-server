package controllers.api

import play.api.libs.json.{Json,JsValue}
import scala.concurrent.Future

import controllers.backend.{StoreBackend,StoreObjectBackend}
import org.overviewproject.models.{Store,StoreObject}

class StoreObjectControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockStoreBackend = mock[StoreBackend]
    val mockObjectBackend = mock[StoreObjectBackend]
    val controller = new StoreObjectController {
      override val storeBackend = mockStoreBackend
      override val storeObjectBackend = mockObjectBackend
    }
  }

  "StoreObjectController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val storeId = 123L
        mockStoreBackend.showOrCreate(request.apiToken.token) returns Future.successful(Store(storeId, "api-tok3n", Json.obj()))
        override def action = controller.index()
      }

      "return JSON with status code 200" in new IndexScope {
        mockObjectBackend.index(storeId) returns Future(Seq())
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return an empty Array when there are no StoreObjects" in new IndexScope {
        mockObjectBackend.index(storeId) returns Future(Seq())
        contentAsString(result) must beEqualTo("[]")
      }

      "return some StoreObjects when there are StoreObjects" in new IndexScope {
        mockObjectBackend.index(storeId) returns Future(Seq(
          factory.storeObject(id=1L, indexedLong=Some(13L), indexedString=Some("foo")),
          factory.storeObject(id=2L, json=Json.obj("foo" -> "bar"))
        ))
        val json = contentAsString(result)
        json must /#(0) /("id" -> 1)
        json must /#(0) /("indexedLong" -> 13)
        json must /#(0) /("indexedString" -> "foo")
        json must /#(1) /("json") /("foo" -> "bar")
      }
    }

    "#show" should {
      trait ShowScope extends BaseScope {
        val storeObjectId = 2L
        override def action = controller.show(storeObjectId)
      }

      "return 404 when not found" in new ShowScope {
        mockObjectBackend.show(storeObjectId) returns Future(None)
        status(result) must beEqualTo(NOT_FOUND)
      }

      "return JSON when found" in new ShowScope {
        mockObjectBackend.show(storeObjectId) returns Future(Some(factory.storeObject(
          id=1L,
          indexedLong=Some(3L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        )))
        val json = contentAsString(result)

        json must /("id" -> 1)
        json must /("indexedLong" -> 3)
        json must /("indexedString" -> "foo")
        json must /("json") /("foo" -> "bar")
      }

      "return `null` JSON properties" in new ShowScope {
        mockObjectBackend.show(storeObjectId) returns Future(Some(factory.storeObject(
          indexedLong=None,
          indexedString=None
        )))
        val json = contentAsString(result)

        json must /("indexedLong" -> null)
        json must /("indexedString" -> null)
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope {
        val storeId = 123L
        mockStoreBackend.showOrCreate(request.apiToken.token) returns Future.successful(Store(storeId, "api-tok3n", Json.obj()))

        val body: JsValue = Json.obj(
          "indexedLong" -> 4L,
          "indexedString" -> "foo",
          "json" -> Json.obj("foo" -> "bar")
        )
        override lazy val result = controller.create()(fakeJsonRequest(body))
      }

      trait CreateManyScope extends CreateScope {
        override val body = Json.arr(
          Json.obj(
            "indexedLong" -> 1L,
            "indexedString" -> "foo",
            "json" -> Json.obj("foo" -> "bar")
          ),
          Json.obj(
            "indexedLong" -> 2L,
            "json" -> Json.obj("bar" -> "baz")
          )
        )
      }

      "create the object" in new CreateScope {
        mockObjectBackend.create(any[Long], any[StoreObject.CreateAttributes]) returns Future(factory.storeObject())
        status(result) must beEqualTo(OK)
        there was one(mockObjectBackend).create(storeId, StoreObject.CreateAttributes(
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        ))
      }

      "create an array of objects" in new CreateManyScope {
        mockObjectBackend.createMany(any[Long], any[Seq[StoreObject.CreateAttributes]]) returns Future(Seq(factory.storeObject(), factory.storeObject()))
        status(result) must beEqualTo(OK)
        there was one(mockObjectBackend).createMany(storeId, Seq(
          StoreObject.CreateAttributes(
            indexedLong=Some(1L),
            indexedString=Some("foo"),
            json=Json.obj("foo" -> "bar")
          ),
          StoreObject.CreateAttributes(
            indexedLong=Some(2L),
            indexedString=None,
            json=Json.obj("bar" -> "baz")
          )
        ))
      }

      "return the JSON object" in new CreateScope {
        mockObjectBackend.create(any[Long], any[StoreObject.CreateAttributes]) returns Future(factory.storeObject(
          id=1L,
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        ))
        val json = contentAsString(result)

        json must /("id" -> 1)
        json must /("indexedLong" -> 4)
        json must /("indexedString" -> "foo")
        json must /("json") /("foo" -> "bar")
      }

      "return the array of JSON objects" in new CreateManyScope {
        mockObjectBackend.createMany(any[Long], any[Seq[StoreObject.CreateAttributes]]) returns Future(Seq(
          factory.storeObject(id=1L, indexedLong=Some(1L)),
          factory.storeObject(indexedString=Some("bar"), json=Json.obj("foo" -> "bar"))
        ))
        val json = contentAsString(result)
        json must /#(0) /("id" -> 1L)
        json must /#(0) /("indexedLong" -> 1L)
        json must /#(1) /("indexedString" -> "bar")
        json must /#(1) /("json") /("foo" -> "bar")
      }

      "allow you to not specify 'indexedLong' or 'indexedString'" in new CreateScope {
        override val body = Json.obj(
          "json" -> Json.obj("foo" -> "bar")
        )
        mockObjectBackend.create(any[Long], any[StoreObject.CreateAttributes]) returns Future(factory.storeObject())
        status(result) must beEqualTo(OK)
        there was one(mockObjectBackend).create(storeId, StoreObject.CreateAttributes(
          indexedLong=None,
          indexedString=None,
          json=Json.obj("foo" -> "bar")
        ))
      }

      "complain if you do not specify json" in new CreateScope {
        override val body = Json.obj("indexedLong" -> 4)
        status(result) must beEqualTo(BAD_REQUEST)
        contentAsString(result) must /("message" ->
          """You must POST a JSON object with "indexedLong" (Number or null), "indexedString" (String or null) and "json" (possibly-empty Object). You may post an Array of such objects to create many objects with one request."""
        )
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val storeObjectId = 2L
        val body = Json.obj(
          "indexedLong" -> 4L,
          "indexedString" -> "foo",
          "json" -> Json.obj("foo" -> "bar")
        )
        override lazy val result = controller.update(storeObjectId)(fakeJsonRequest(body))
      }

      "return 404 when there is no StoreObject" in new UpdateScope {
        mockObjectBackend.update(any[Long], any[StoreObject.UpdateAttributes]) returns Future(None)
        status(result) must beEqualTo(NOT_FOUND)
      }

      "return 400 when there is no `json`" in new UpdateScope {
        override val body = Json.obj("indexedLong" -> 4L, "indexedString" -> "foo")
        status(result) must beEqualTo(BAD_REQUEST)
        contentAsString(result) must /("message" ->
          """You must POST a JSON object with "indexedLong" (Number or null), "indexedString" (String or null) and "json" (possibly-empty Object)"""
        )
      }

      "update the backend" in new UpdateScope {
        mockObjectBackend.update(any[Long], any[StoreObject.UpdateAttributes]) returns Future(Some(factory.storeObject()))
        status(result) must beEqualTo(OK)
        there was one(mockObjectBackend).update(storeObjectId, StoreObject.UpdateAttributes(
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        ))
      }

      "return the updated object" in new UpdateScope {
        val storeObject = factory.storeObject(indexedLong=Some(4L), indexedString=Some("foo"), json=Json.obj("foo" -> "baz"))
        mockObjectBackend.update(any[Long], any[StoreObject.UpdateAttributes]) returns Future(Some(storeObject))
        val json = contentAsString(result)
        json must /("indexedLong" -> 4L)
        json must /("indexedString" -> "foo")
        json must /("json") /("foo" -> "baz")
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val storeObjectId = 2L
        override def action = controller.destroy(storeObjectId)
        mockObjectBackend.destroy(any[Long]) returns Future.successful(Unit)
      }

      "destroy a StoreObject" in new DestroyScope {
        status(result) must beEqualTo(NO_CONTENT)
        there was one(mockObjectBackend).destroy(storeObjectId)
      }
    }

    "#destroyMany" should {
      trait DestroyManyScope extends BaseScope {
        val storeId = 123L
        mockStoreBackend.showOrCreate(request.apiToken.token) returns Future.successful(Store(storeId, "api-tok3n", Json.obj()))

        val body: JsValue = Json.arr(2L, 3L, 4L)
        override lazy val result = controller.destroyMany()(fakeJsonRequest(body))
        mockObjectBackend.destroyMany(any[Long], any[Seq[Long]]) returns Future.successful(())
      }

      "destroy the objects" in new DestroyManyScope {
        status(result) must beEqualTo(NO_CONTENT)
        there was one(mockObjectBackend).destroyMany(storeId, Seq(2L, 3L, 4L))
      }

      "error on invalid input" in new DestroyManyScope {
        override val body: JsValue = Json.arr(2L, "foo", 3L)
        status(result) must beEqualTo(BAD_REQUEST)
        contentAsString(result) must /("message" -> "You must POST a JSON Array of IDs.")
      }
    }
  }
}
