package controllers.api

import play.api.libs.json.{Json,JsValue}
import scala.concurrent.Future

import controllers.backend.VizObjectBackend
import org.overviewproject.models.VizObject

class VizObjectControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[VizObjectBackend]
    val controller = new VizObjectController {
      override val backend = mockBackend
    }
  }

  "VizObjectController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val vizId = 1L
        override def action = controller.index(vizId)
      }

      "return JSON with status code 200" in new IndexScope {
        mockBackend.index(vizId) returns Future(Seq())
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return an empty Array when there are no VizObjects" in new IndexScope {
        mockBackend.index(vizId) returns Future(Seq())
        contentAsString(result) must beEqualTo("[]")
      }

      "return some VizObjects when there are VizObjects" in new IndexScope {
        mockBackend.index(vizId) returns Future(Seq(
          factory.vizObject(id=1L, indexedLong=Some(13L), indexedString=Some("foo")),
          factory.vizObject(id=2L, json=Json.obj("foo" -> "bar"))
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
        val vizId = 1L
        val vizObjectId = 2L
        override def action = controller.show(vizId, vizObjectId)
      }

      "return 404 when not found" in new ShowScope {
        mockBackend.show(vizObjectId) returns Future(None)
        status(result) must beEqualTo(NOT_FOUND)
      }

      "return JSON when found" in new ShowScope {
        mockBackend.show(vizObjectId) returns Future(Some(factory.vizObject(
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
        mockBackend.show(vizObjectId) returns Future(Some(factory.vizObject(
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
        val vizId = 1L
        val body: JsValue = Json.obj(
          "indexedLong" -> 4L,
          "indexedString" -> "foo",
          "json" -> Json.obj("foo" -> "bar")
        )
        override lazy val result = controller.create(vizId)(fakeJsonRequest(body))
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
        mockBackend.create(any[Long], any[VizObject.CreateAttributes]) returns Future(factory.vizObject())
        status(result) must beEqualTo(OK)
        there was one(mockBackend).create(vizId, VizObject.CreateAttributes(
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        ))
      }

      "create an array of objects" in new CreateManyScope {
        mockBackend.createMany(any[Long], any[Seq[VizObject.CreateAttributes]]) returns Future(Seq(factory.vizObject(), factory.vizObject()))
        status(result) must beEqualTo(OK)
        there was one(mockBackend).createMany(vizId, Seq(
          VizObject.CreateAttributes(
            indexedLong=Some(1L),
            indexedString=Some("foo"),
            json=Json.obj("foo" -> "bar")
          ),
          VizObject.CreateAttributes(
            indexedLong=Some(2L),
            indexedString=None,
            json=Json.obj("bar" -> "baz")
          )
        ))
      }

      "return the JSON object" in new CreateScope {
        mockBackend.create(any[Long], any[VizObject.CreateAttributes]) returns Future(factory.vizObject(
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
        mockBackend.createMany(any[Long], any[Seq[VizObject.CreateAttributes]]) returns Future(Seq(
          factory.vizObject(id=1L, indexedLong=Some(1L)),
          factory.vizObject(indexedString=Some("bar"), json=Json.obj("foo" -> "bar"))
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
        mockBackend.create(any[Long], any[VizObject.CreateAttributes]) returns Future(factory.vizObject())
        status(result) must beEqualTo(OK)
        there was one(mockBackend).create(vizId, VizObject.CreateAttributes(
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
        val vizId = 1L
        val vizObjectId = 2L
        val body = Json.obj(
          "indexedLong" -> 4L,
          "indexedString" -> "foo",
          "json" -> Json.obj("foo" -> "bar")
        )
        override lazy val result = controller.update(vizId, vizObjectId)(fakeJsonRequest(body))
      }

      "return 404 when there is no VizObject" in new UpdateScope {
        mockBackend.update(any[Long], any[VizObject.UpdateAttributes]) returns Future(None)
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
        mockBackend.update(any[Long], any[VizObject.UpdateAttributes]) returns Future(Some(factory.vizObject()))
        status(result) must beEqualTo(OK)
        there was one(mockBackend).update(vizObjectId, VizObject.UpdateAttributes(
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        ))
      }

      "return the updated object" in new UpdateScope {
        val vizObject = factory.vizObject(indexedLong=Some(4L), indexedString=Some("foo"), json=Json.obj("foo" -> "baz"))
        mockBackend.update(any[Long], any[VizObject.UpdateAttributes]) returns Future(Some(vizObject))
        val json = contentAsString(result)
        json must /("indexedLong" -> 4L)
        json must /("indexedString" -> "foo")
        json must /("json") /("foo" -> "baz")
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val vizId = 1L
        val vizObjectId = 2L
        override def action = controller.destroy(vizId, vizObjectId)
        mockBackend.destroy(any[Long]) returns Future.successful(Unit)
      }

      "destroy a VizObject" in new DestroyScope {
        status(result) must beEqualTo(NO_CONTENT)
        there was one(mockBackend).destroy(vizObjectId)
      }
    }
  }
}
