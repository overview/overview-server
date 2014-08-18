package controllers.api

import play.api.libs.json.Json
import play.api.mvc.AnyContentAsJson
import scala.concurrent.Future

import controllers.backend.VizBackend
import org.overviewproject.models.Viz

class VizControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[VizBackend]
    val controller = new VizController {
      override val backend = mockBackend
    }
  }

  "VizController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSetId = 1L
        def vizs: Seq[Viz] = Seq()
        mockBackend.index(documentSetId) returns Future(vizs) // async, so we get overrides
        override def action = controller.index(documentSetId)
      }

      "return JSON with status code 200" in new IndexScope {
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return an empty Array when there are no vizs" in new IndexScope {
        override def vizs = Seq()
        contentAsString(result) must beEqualTo("[]")
      }

      "return some Vizs when there are Vizs" in new IndexScope {
        override def vizs = Seq(
          factory.viz(url="http://example.org/1"),
          factory.viz(url="http://example.org/2")
        )
        val json = contentAsString(result)
        json must /#(0) /("url" -> "http://example.org/1")
        json must /#(1) /("url" -> "http://example.org/2")
      }
    }

    "#show" should {
      trait ShowScope extends BaseScope {
        val vizId = 1L
        def viz: Option[Viz]
        mockBackend.show(vizId) returns Future(viz) // async
        override def action = controller.show(vizId)
      }

      "return 404 when not found" in new ShowScope {
        override def viz = None
        status(result) must beEqualTo(NOT_FOUND)
      }

      "return JSON when found" in new ShowScope {
        override def viz = Some(factory.viz(
          url="http://example.org/1",
          title="title",
          createdAt=new java.sql.Timestamp(1408387783304L),
          json=Json.obj("foo" -> "bar")
        ))

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("url" -> "http://example.org/1")
        json must /("title" -> "title")
        json must /("createdAt" -> "2014-08-18T18:49:43.304Z")
        json must /("json") /("foo" -> "bar")
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val vizId = 1L
        val body = Json.obj(
          "title" -> "a title",
          "json" -> Json.obj("foo" -> "bar")
        )
        override lazy val result = controller.update(vizId)(fakeJsonRequest(body))
      }

      "return a 404 error when there is no Viz" in new UpdateScope {
        mockBackend.update(any[Long], any[Viz.UpdateAttributes]) returns Future(None)
        status(result) must beEqualTo(NOT_FOUND)
      }

      "return a 400 error on empty title" in new UpdateScope {
        override val body = Json.obj(
          "title" -> "",
          "json" -> Json.obj("foo" -> "bar")
        )
        status(result) must beEqualTo(BAD_REQUEST)
        contentAsString(result) must /("message" -> """You must POST a JSON object with "title" (non-empty String) and "json" (possibly-empty Object)""")
      }

      "return a 400 error on empty json" in new UpdateScope {
        override val body = Json.obj(
          "title" -> "title"
        )
        status(result) must beEqualTo(BAD_REQUEST)
        contentAsString(result) must /("message" -> """You must POST a JSON object with "title" (non-empty String) and "json" (possibly-empty Object)""")
      }

      "update the backend" in new UpdateScope {
        mockBackend.update(any[Long], any[Viz.UpdateAttributes]) returns Future(Some(factory.viz()))
        status(result)
        there was one(mockBackend).update(vizId, Viz.UpdateAttributes(
          title="a title",
          json=Json.obj("foo" -> "bar")
        ))
      }

      "return the updated object" in new UpdateScope {
        val viz = factory.viz(title="new title", json=Json.obj("foo" -> "baz"))
        mockBackend.update(any[Long], any[Viz.UpdateAttributes]) returns Future(Some(viz))
        val json = contentAsString(result)
        json must /("id" -> viz.id)
        json must /("title" -> "new title")
        json must /("json") /("foo" -> "baz")
      }
    }
  }
}
